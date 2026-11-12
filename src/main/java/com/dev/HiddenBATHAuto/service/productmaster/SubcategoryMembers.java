package com.dev.HiddenBATHAuto.service.productmaster;

import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubcategoryMembers {
  private final JdbcTemplate db;
  public SubcategoryMembers(@Qualifier("dataSource") DataSource data) { db = new JdbcTemplate(data); }
  public record Category(long id,String name,String kind,long count,long version,List<Long> members) {}
  public record Candidate(long id,String name,String company,String kind) {}
  public record Edit(String name,long version,List<Long> members) {}
  public List<Category> categories() {
    Map<Long,List<Long>> members = new HashMap<>();
    db.query("select value_id, member_id from tb_pm_subcategory_member order by member_id", rs -> {
      members.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getLong(2));
    });
    return db.query("""
        select v.id,v.management_label,p.member_kind,
          (select count(distinct c.product_id) from tb_pm_product_component c where c.value_id=v.id),
          v.row_version from tb_pm_attribute_value v join tb_pm_attribute_group g on g.id=v.group_id
          left join tb_pm_subcategory_policy p on p.value_id=v.id
          where g.system_role='SUBCATEGORY' order by v.sort_order,v.id
        """,(rs,n)->new Category(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getLong(5),members.getOrDefault(rs.getLong(1),List.of())));
  }
  public List<Candidate> candidates() {
    return db.query("""
      select m.id,m.name,c.name,if(m.role='CUSTOMER_REPRESENTATIVE','PARTNER','STAFF')
      from tb_member m left join tb_company c on c.id=m.company_id where m.enabled=1 and
      (m.role in ('ADMIN','MANAGEMENT','INTERNAL_EMPLOYEE') or (m.role='CUSTOMER_REPRESENTATIVE' and c.id is not null))
      order by c.name,m.name,m.id
      """,(rs,n)->new Candidate(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4)));
  }
  private void lock(long id,long version) {
    List<Long> rows=db.queryForList("""
      select v.row_version from tb_pm_attribute_value v join tb_pm_attribute_group g on g.id=v.group_id
      where v.id=? and g.system_role='SUBCATEGORY' for update
      """,Long.class,id);
    if (!rows.equals(List.of(version))) throw new IllegalStateException("중분류가 변경되었습니다. 다시 조회해 주세요.");
  }
  @Transactional
  public void edit(long id,Edit edit,String actor) {
    lock(id,edit.version());
    String name=Objects.requireNonNullElse(edit.name(),"").trim();
    if(name.isEmpty() || name.length()>120) throw new IllegalArgumentException("중분류 명칭을 1~120자로 입력해 주세요.");
    Category category=categories().stream().filter(c->c.id()==id).findFirst().orElseThrow();
    Set<Long> selected=new LinkedHashSet<>(Objects.requireNonNull(edit.members()));
    Set<Long> allowed=new HashSet<>();
    candidates().stream().filter(c->c.kind().equals(category.kind())).forEach(c->allowed.add(c.id()));
    for(long member:selected) if(!category.members().contains(member)) {
      if(!allowed.contains(member)) throw new IllegalArgumentException("해당 중분류에 등록 가능한 계정을 선택해 주세요.");
      db.update("insert into tb_pm_subcategory_member(value_id,member_id) values (?,?)",id,member);
    }
    for(long member:category.members()) if(!selected.contains(member)) {
      if(!db.queryForList("select product_id from tb_pm_product_subcategory_member where value_id=? and member_id=? limit 1",Long.class,id,member).isEmpty())
        throw new IllegalStateException("제품에서 배정 중인 멤버입니다. 제품의 중분류 배정을 먼저 해제해 주세요.");
      db.update("delete from tb_pm_subcategory_member where value_id=? and member_id=?",id,member);
    }
    db.update("update tb_pm_attribute_value set customer_label=?,management_label=?,production_label=?,updated_by=?,updated_at=now(),row_version=row_version+1 where id=?",name,name,name,actor,id);
  }
  public Map<Long,List<Long>> assigned(long product) {
    Map<Long,List<Long>> result=new LinkedHashMap<>();
    db.query("select value_id,member_id from tb_pm_product_subcategory_member where product_id=? order by value_id,member_id",rs->{
      result.computeIfAbsent(rs.getLong(1),k->new ArrayList<>()).add(rs.getLong(2));
    },product);
    return result;
  }
  @Transactional
  public void save(long product,Map<Long,List<Long>> selection) {
    if(selection==null) return;
    Set<Long> attached=new HashSet<>(db.queryForList("""
      select c.value_id from tb_pm_product_component c join tb_pm_subcategory_policy p on p.value_id=c.value_id
      where c.product_id=? order by c.value_id
      """,Long.class,product));
    if(!selection.keySet().equals(attached)) throw new IllegalArgumentException("선택한 중분류별로 멤버를 지정해 주세요.");
    db.update("delete from tb_pm_product_subcategory_member where product_id=?",product);
    for(var entry:selection.entrySet()) {
      var members=new LinkedHashSet<>(entry.getValue());
      if(members.isEmpty()) throw new IllegalArgumentException("중분류별로 멤버를 한 명 이상 선택해 주세요.");
      for(long member:members) {
        int added=db.update("""
          insert into tb_pm_product_subcategory_member(product_id,value_id,member_id)
          select ?,sm.value_id,sm.member_id from tb_pm_subcategory_member sm
          join tb_pm_subcategory_policy p on p.value_id=sm.value_id
          join tb_member m on m.id=sm.member_id
          where sm.value_id=? and sm.member_id=? and m.enabled=1 and
          ((p.member_kind='STAFF' and m.role in ('ADMIN','MANAGEMENT','INTERNAL_EMPLOYEE'))
          or (p.member_kind='PARTNER' and m.role='CUSTOMER_REPRESENTATIVE' and m.company_id is not null))
          """,product,entry.getKey(),member);
        if(added!=1) throw new IllegalArgumentException("중분류의 멤버 명단이 변경되었습니다. 다시 조회해 주세요.");
      }
    }
  }
  @Transactional
  public void copy(long source,long target) {
    db.update("""
      insert into tb_pm_product_subcategory_member(product_id,value_id,member_id)
      select distinct ?,a.value_id,a.member_id from tb_pm_product_subcategory_member a
      join tb_pm_product_component c on c.value_id=a.value_id and c.product_id=? where a.product_id=?
      """,target,target,source);
  }
}
