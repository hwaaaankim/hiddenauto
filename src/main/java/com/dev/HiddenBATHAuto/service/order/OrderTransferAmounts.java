package com.dev.HiddenBATHAuto.service.order;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.dev.HiddenBATHAuto.model.task.Order;
import com.dev.HiddenBATHAuto.model.task.Task;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** Allocate task charges against the locked, pre-edit order rows. */
@Service
public class OrderTransferAmounts {
    @PersistenceContext
    private EntityManager entityManager;

    public record Plan(int unit, int quantity, int supply, int gross, int packing, int delivery,
                       int sourceTotal, int destinationTotal) {
        public void applyLine(Order order) {
            order.setProductCost(unit);
            order.setQuantity(quantity);
            order.setSupplyPrice(supply);
            order.setTotalAmount(gross);
            order.setPackingCost(packing);
            order.setDeliveryCost(delivery);
        }
    }

    private record Fees(int packing, int delivery) {
        long sum() { return (long) packing + delivery; }
    }

    public Plan prepare(Task source, Order selected, boolean split, int unit, int quantity, int supply,
                        int gross, int packing, int delivery) {
        if ((unit < 0 && unit != selected.getProductCost()) || packing < 0 || delivery < 0) {
            throw new IllegalArgumentException("단가·포장비·배송비는 0원 이상이어야 합니다.");
        }
        boolean sameMoney = unit == selected.getProductCost() && quantity == selected.getQuantity()
                && supply == selected.getSupplyPrice() && gross == selected.getTotalAmount()
                && packing == selected.getPackingCost() && delivery == selected.getDeliveryCost();
        if (!split && sameMoney) {
            return new Plan(unit, quantity, supply, gross, packing, delivery, source.getTotalPrice(), 0);
        }

        List<Order> rows = source.getOrders();
        if (rows == null || rows.stream().noneMatch(row -> Objects.equals(row.getId(), selected.getId()))) {
            throw new IllegalStateException("발주의 소속 오더가 바뀌었습니다. 다시 조회해 주세요.");
        }
        long storedSum = 0;
        Map<Fees, Integer> counts = new HashMap<>();
        for (Order row : rows) {
            Fees fees = new Fees(row.getPackingCost(), row.getDeliveryCost());
            counts.merge(fees, 1, Integer::sum);
            storedSum += row.getTotalAmount() + fees.sum();
        }
        long oldPacking = selected.getPackingCost();
        long oldDelivery = selected.getDeliveryCost();
        long difference = (long) source.getTotalPrice() - storedSum;

        if (difference < 0) {
            // Historical management registration wrote the same task charge on every row.
            // Distinct charges on other rows may have been entered explicitly since then.
            Fees shared = sharedRegistrationFees(source, rows, counts, -difference);
            if (shared == null) throw unbalanced(source, storedSum);
            boolean selectedSharesFees = shared.equals(new Fees(selected.getPackingCost(), selected.getDeliveryCost()));
            boolean feeEdit = packing != selected.getPackingCost() || delivery != selected.getDeliveryCost();
            if (selectedSharesFees && (split || feeEdit)) {
                // The original task retains its shared charge while another carrier remains.
                oldPacking = 0;
                oldDelivery = 0;
                if (packing == shared.packing()) packing = 0;
                if (delivery == shared.delivery()) delivery = 0;
            }
        } else if (difference > 0) {
            throw unbalanced(source, storedSum);
        }

        long before = (long) selected.getTotalAmount() + oldPacking + oldDelivery;
        long after = (long) gross + packing + delivery;
        long sourceTotal = (long) source.getTotalPrice() - before + (split ? 0 : after);
        return new Plan(unit, quantity, supply, gross, packing, delivery, checked(sourceTotal, "원 발주 합계"),
                split ? checked(after, "분리 발주 합계") : 0);
    }

    public record DisplayLine(long unit, long supply, long gross) {
        public boolean reviewRequired() {
            return (unit == 0 && supply != 0) || ((supply == 0) != (gross == 0));
        }
    }
    public record DisplayTotals(long packing, long delivery, boolean breakdownNeedsReview) {}

    private static final List<String> FORM_REGISTRATIONS = List.of("MANAGEMENT_FORM_ORDER_CREATED",
            "NORMAL_REGISTER_MANAGEMENT_FORM_ORDER_CREATED", "URGENT_REGISTER_MANAGEMENT_FORM_ORDER_CREATED");

    /** Only mixed legacy fee patterns need registration evidence. Batch it for list and Excel reads. */
    public Set<Long> confirmedFormRows(List<Task> tasks) {
        Set<Long> ids = new java.util.LinkedHashSet<>();
        for (Task task : tasks) {
            if (task == null || task.getOrders() == null || task.getOrders().size() < 2) continue;
            Map<Fees,Integer> counts = new HashMap<>();
            long sum = 0;
            for (Order row : task.getOrders()) {
                Fees fees = new Fees(row.getPackingCost(), row.getDeliveryCost());
                counts.merge(fees, 1, Integer::sum);
                sum += row.getTotalAmount() + fees.sum();
            }
            if (counts.size() < 2 || sum <= task.getTotalPrice()) continue;
            Fees candidate = sharedCandidate(counts, sum - task.getTotalPrice());
            if (candidate != null) task.getOrders().stream().filter(row -> matches(row, candidate))
                    .map(Order::getId).forEach(ids::add);
        }
        List<Long> all = List.copyOf(ids);
        Set<Long> found = new java.util.HashSet<>();
        for (int i = 0; i < all.size(); i += 800) {
            found.addAll(entityManager.createQuery("""
                    select distinct e.order.id from OrderChangeEvent e
                    where e.taskIdSnapshot = e.order.task.id and e.order.id in :ids and e.operationCode in :codes
                    """, Long.class)
                    .setParameter("ids", all.subList(i, Math.min(i + 800, all.size())))
                    .setParameter("codes", FORM_REGISTRATIONS).getResultList());
        }
        return found;
    }

    public DisplayLine displayLine(Order row) {
        return new DisplayLine(row.getProductCost(), row.getSupplyPrice(), row.getTotalAmount());
    }

    public DisplayTotals displayTotals(Task task, List<Order> rows, Set<Long> confirmedFormRows) {
        long packing = 0, delivery = 0, goods = 0;
        boolean incompleteLine = false;
        Map<Fees, Integer> counts = new HashMap<>();
        for (Order row : rows) {
            packing += row.getPackingCost();
            delivery += row.getDeliveryCost();
            goods += row.getTotalAmount();
            incompleteLine |= displayLine(row).reviewRequired();
            counts.merge(new Fees(row.getPackingCost(), row.getDeliveryCost()), 1, Integer::sum);
        }
        long difference = task.getTotalPrice() - (goods + packing + delivery);
        if (difference < 0) {
            Fees candidate = sharedCandidate(counts, -difference);
            if (candidate != null && (counts.size() == 1 || rows.stream().filter(row -> matches(row, candidate))
                    .allMatch(row -> confirmedFormRows.contains(row.getId())))) {
                long duplicates = counts.get(candidate) - 1L;
                return new DisplayTotals(packing - duplicates * candidate.packing(),
                        delivery - duplicates * candidate.delivery(), incompleteLine);
            }
        }
        return new DisplayTotals(packing, delivery, incompleteLine || difference != 0);
    }

    /** Allocate only the selected parcel rows across all workbooks in one download. */
    public Map<Long, Integer> statementPacking(List<Order> selection) {
        Map<Long,Integer> amounts = new HashMap<>();
        Map<Task,List<Order>> groups = new java.util.LinkedHashMap<>();
        for (Order row : selection) {
            amounts.put(row.getId(), row.getPackingCost());
            if (row.getTask() != null && row.getPackingCost() > 0) {
                groups.computeIfAbsent(row.getTask(), ignored -> new java.util.ArrayList<>()).add(row);
            }
        }
        List<Task> candidates = groups.entrySet().stream()
                .filter(entry -> entry.getValue().stream().map(row -> new Fees(row.getPackingCost(), row.getDeliveryCost()))
                        .distinct().count() < entry.getValue().size())
                .map(Map.Entry::getKey).toList();
        Set<Long> confirmed = confirmedFormRows(candidates);
        for (Task task : candidates) {
            List<Order> all = task.getOrders();
            Map<Fees,Integer> counts = new HashMap<>();
            long raw = 0;
            for (Order row : all) {
                Fees fees = new Fees(row.getPackingCost(), row.getDeliveryCost());
                raw += row.getTotalAmount() + fees.sum();
                counts.merge(fees, 1, Integer::sum);
            }
            if (raw == task.getTotalPrice()) continue; // equal charges may be independently billed
            Fees shared = sharedCandidate(counts, raw - task.getTotalPrice());
            if (shared == null) {
                // Total and line charges disagree; repeated charges cannot be identified as separate or shared.
                Map<Fees,Long> selectedCounts = groups.get(task).stream().collect(Collectors.groupingBy(
                        row -> new Fees(row.getPackingCost(), row.getDeliveryCost()), Collectors.counting()));
                groups.get(task).stream().filter(row -> selectedCounts.get(new Fees(row.getPackingCost(), row.getDeliveryCost())) > 1)
                        .forEach(row -> amounts.remove(row.getId()));
                continue;
            }
            if (shared.packing() == 0) continue;
            boolean known = counts.size() == 1 || all.stream().filter(row -> matches(row, shared))
                    .allMatch(row -> confirmed.contains(row.getId()));
            boolean alreadyPrinted = false;
            for (Order row : groups.get(task)) {
                if (!matches(row, shared)) continue;
                if (!known) amounts.remove(row.getId());
                else if (alreadyPrinted) amounts.put(row.getId(), 0);
                alreadyPrinted = true;
            }
        }
        return amounts;
    }

    private static boolean matches(Order row, Fees fees) {
        return row.getPackingCost() == fees.packing() && row.getDeliveryCost() == fees.delivery();
    }

    private Fees sharedRegistrationFees(Task task, List<Order> rows, Map<Fees, Integer> counts, long excess) {
        Fees shared = sharedCandidate(counts, excess);
        if (shared == null || counts.size() == 1) return shared;
        List<Order> repeated = rows.stream()
                .filter(row -> row.getPackingCost() == shared.packing() && row.getDeliveryCost() == shared.delivery())
                .toList();
        return allRegistered(task, repeated, FORM_REGISTRATIONS) ? shared : null;
    }

    private static Fees sharedCandidate(Map<Fees, Integer> counts, long excess) {
        List<Fees> candidates = counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1 && entry.getKey().sum() > 0
                        && (entry.getValue() - 1L) * entry.getKey().sum() == excess)
                .map(Map.Entry::getKey).toList();
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private boolean allRegistered(Task task, List<Order> rows, List<String> codes) {
        Set<Long> ids = rows.stream().map(Order::getId).collect(Collectors.toSet());
        long count = entityManager.createQuery("""
                select count(distinct e.order.id) from OrderChangeEvent e
                where e.taskIdSnapshot = :taskId and e.order.id in :ids and e.operationCode in :codes
                """, Long.class)
                .setParameter("taskId", task.getId()).setParameter("ids", ids).setParameter("codes", codes)
                .getSingleResult();
        return count == ids.size();
    }

    private static IllegalStateException unbalanced(Task task, long sum) {
        return new IllegalStateException("발주 #" + task.getId() + "의 저장 합계 " + task.getTotalPrice()
                + "원과 오더별 총액·부대비 합계 " + sum + "원이 일치하지 않습니다. "
                + "기존 발주 명세에서 개별 상품 금액과 공통 운임·포장비의 귀속을 확인한 뒤 다시 저장해 주세요.");
    }

    private static int checked(long amount, String label) {
        try { return Math.toIntExact(amount); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException(label + "이 저장 가능한 금액 범위를 초과했습니다.", ex); }
    }
}
