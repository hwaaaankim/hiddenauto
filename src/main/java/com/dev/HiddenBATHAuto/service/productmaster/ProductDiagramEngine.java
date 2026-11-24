package com.dev.HiddenBATHAuto.service.productmaster;

import static com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.*;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductStudioDtos.Process;
import static com.dev.HiddenBATHAuto.dto.productmaster.ProductDiagramDtos.*;
import com.dev.HiddenBATHAuto.dto.productmaster.ProductDiagramDtos.Variant;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/** Pure geometry/validation shared by every actual answer path. No XML is evaluated. */
public final class ProductDiagramEngine {
  public static final int MAX_SIZE = 1_000_000, MAX_ITEMS = 500, MAX_VARIANTS = 100, MAX_MARKS = 100;
  private ProductDiagramEngine() {}
  private static void require(boolean ok, String message) { if (!ok) throw new IllegalArgumentException(message); }
  private static <T> List<T> list(List<T> v) { return v == null ? List.of() : v; }
  private static void key(String k) { require(k != null && k.matches("[A-Za-z0-9_-]{1,80}"), "도형 식별값이 올바르지 않습니다."); }

  public static List<Issue> structure(Process p) {
    List<Issue> issues = new ArrayList<>();
    if (p.diagrams().size() > 100) return List.of(new Issue("ERROR", "DIAGRAM", "도면 응답", "도면 질문은 최대 100개입니다."));
    for (var e : p.diagrams().entrySet()) {
      try { config(p, e.getKey(), e.getValue()); }
      catch (IllegalArgumentException ex) { issues.add(new Issue("ERROR", "DIAGRAM", "도면 응답", ex.getMessage(), e.getKey())); }
    }
    return issues;
  }
  public static void config(Process p, String owner, Config c) {
    Question q = p.questions().stream().filter(x -> owner.equals(x.key())).findFirst().orElse(null);
    require(q != null && q.control() == Control.NUMBER && !q.fixed(), "도면은 고객 숫자입력 질문에 연결해 주세요.");
    require(c != null, "도면 설정이 없습니다."); key(c.revision());
    require(Set.of("POSITION", "FILL").contains(c.mode()), "도면 완료 방식을 선택해 주세요.");
    reference(p, owner, c.width()); reference(p, owner, c.height());
    require(!list(c.variants()).isEmpty() && list(c.variants()).size() <= MAX_VARIANTS, "도형 종류는 1~100개 등록해 주세요.");
    Set<String> ids = new HashSet<>(), fields = q.fields().stream().map(Field::key).collect(Collectors.toSet());
    if(c.fieldLabels()!=null)require(c.fieldLabels().size()<=20 && c.fieldLabels().entrySet().stream().allMatch(e -> fields.contains(e.getKey()) && e.getValue()!=null && e.getValue().length()<=120),"도면 개수 항목의 표시명을 확인해 주세요.");
    Set<String> connected = new HashSet<>();
    for (Variant v : c.variants()) {
      require(v != null, "빈 도형 종류는 저장할 수 없습니다."); key(v.key());
      require(ids.add(v.key()), "도형 식별값이 중복됩니다.");
      require(fields.contains(v.fieldKey()), "도형이 연결된 개수 항목이 없습니다."); connected.add(v.fieldKey());
      require(v.name() != null && !v.name().isBlank() && v.name().length() <= 120, "도형 이름은 1~120자로 입력해 주세요.");
      require(Set.of("RECT", "ELLIPSE").contains(v.shape()), "사각형 또는 원형·타원형을 선택해 주세요.");
      require(!"FILL".equals(c.mode()) || "RECT".equals(v.shape()), "꽉 채우기에서는 사각형만 사용할 수 있습니다.");
      dimension(p, owner, v.width()); dimension(p, owner, v.height());
      require(list(v.conditions()).size() <= 20, "도형 표시 조건은 최대 20개입니다.");
      for (Condition condition : list(v.conditions())) {
        require(condition != null, "도형 표시 조건을 확인해 주세요.");
        Question source = before(p, owner, condition.groupKey());
        if (source.control() == Control.NUMBER) {
          require(source.fields().stream().anyMatch(f -> f.key().equals(condition.fieldKey())), "도형 조건의 숫자 필드가 없습니다.");
          require(Set.of(Operator.EQ,Operator.NE,Operator.GT,Operator.GE,Operator.LT,Operator.LE,Operator.RANGE,Operator.PRESENT,Operator.ABSENT).contains(condition.operator()), "도형의 숫자 조건을 확인해 주세요.");
          if (!Set.of(Operator.PRESENT,Operator.ABSENT).contains(condition.operator())) {
            require(condition.lower() != null, "도형 조건의 기준값을 입력해 주세요.");
            if (condition.operator()==Operator.RANGE) require(condition.upper()!=null && (condition.upper().compareTo(condition.lower())>0 || condition.upper().compareTo(condition.lower())==0 && condition.lowerInclusive() && condition.upperInclusive()), "도형 조건 범위를 확인해 주세요.");
          }
        } else {
          require(source.control()==Control.RADIO || source.control()==Control.CHECKBOX, "도형 표시 조건은 앞의 선택형·숫자입력 질문을 사용합니다.");
          require(Set.of(Operator.EQ,Operator.NE,Operator.PRESENT,Operator.ABSENT).contains(condition.operator()), "도형의 선택 조건을 확인해 주세요.");
          if (condition.operator()==Operator.EQ || condition.operator()==Operator.NE) require(!list(condition.choiceKeys()).isEmpty()
              && list(condition.choiceKeys()).stream().allMatch(k -> source.choices().stream().anyMatch(x -> x.key().equals(k))), "도형 조건의 선택지가 없습니다.");
        }
      }
      require(list(v.marks()).size() <= MAX_MARKS, "내부 기호는 도형당 최대 100개입니다.");
      for (Mark m : list(v.marks())) require(m != null && Set.of("LINE","RECT","ELLIPSE","TRIANGLE_LEFT","TRIANGLE_RIGHT","TEXT").contains(m.kind())
          && m.x1()>=0 && m.x1()<=1000 && m.x2()>=0 && m.x2()<=1000 && m.y1()>=0 && m.y1()<=1000 && m.y2()>=0 && m.y2()<=1000
          && (m.text()==null || m.text().length()<=80), "내부 기호는 도형 안에 그려 주세요. 문자는 80자까지입니다.");
    }
    require(connected.containsAll(fields), "모든 개수 항목에 도형을 한 개 이상 등록해 주세요.");
    for (Field f : q.fields()) require(!f.allowNegative() && !f.useDefault() && (f.step()==null || f.step().compareTo(BigDecimal.ONE)==0)
        && (f.min()==null || f.min().signum()>=0 && f.min().stripTrailingZeros().scale()<=0)
        && (f.max()==null || f.max().stripTrailingZeros().scale()<=0), "도면 개수 항목은 음수·소수·자동 기본값을 사용할 수 없습니다. 간격은 1개로 설정해 주세요.");
  }
  private static Question before(Process p, String owner, String source) {
    for (Question q : p.questions()) { if (q.key().equals(owner)) break; if (q.key().equals(source)) return q; }
    throw new IllegalArgumentException("도면은 현재 질문보다 앞의 질문을 참조해야 합니다. 순서와 참조를 확인해 주세요.");
  }
  private static void reference(Process p, String owner, Ref r) {
    require(r!=null, "도면 가로·높이의 앞 질문 필드를 선택해 주세요.");
    Question q=before(p,owner,r.questionKey());
    require(q.control()==Control.NUMBER && q.fields().stream().anyMatch(f -> f.key().equals(r.fieldKey())), "참조할 숫자입력 필드를 찾을 수 없습니다.");
  }
  private static void dimension(Process p,String owner,Dimension d) {
    require(d!=null && d.mode()!=null, "도형 가로·높이의 치수 방식을 선택해 주세요.");
    switch(d.mode()) {
      case "FIXED" -> size(d.value());
      case "REFERENCE" -> reference(p,owner,d.source());
      case "RANGE" -> { require(d.min()!=null && d.max()!=null,"치수 범위의 최소·최대값을 입력해 주세요.");
        require(d.min()>=0 && d.max()<=MAX_SIZE && min(d)<=max(d) && min(d)>0,"치수 범위가 비어 있거나 허용 크기를 벗어났습니다."); }
      default -> throw new IllegalArgumentException("도형 치수 방식이 올바르지 않습니다.");
    }
  }
  private static void size(Integer n) { require(n!=null && n>0 && n<=MAX_SIZE,"치수는 1~1,000,000mm의 정수로 입력해 주세요."); }
  public static int min(Dimension d) { return d.min() + (d.minInclusive()?0:1); }
  public static int max(Dimension d) { return d.max() - (d.maxInclusive()?0:1); }
  private static int value(Ref r,Map<String,Answer> answers) {
    Answer a=answers.get(r.questionKey()); Object n=a==null || a.fields()==null?null:a.fields().get(r.fieldKey());
    require(n!=null,"도면 크기에 필요한 앞 질문의 숫자 답변이 없습니다. 분기와 입력값을 확인해 주세요.");
    try { int i=new BigDecimal(n.toString()).intValueExact();size(i);return i; }
    catch (ArithmeticException | NumberFormatException ex) { throw new IllegalArgumentException("도면 치수로 사용하는 앞 답변은 소수 없이 양의 정수 mm로 입력해 주세요."); }
  }
  public static boolean available(Variant v,Map<String,Answer> answers) {
    return list(v.conditions()).stream().allMatch(c -> answers.containsKey(c.groupKey()) && ProductStudioEngine.matches(c, answers.get(c.groupKey())));
  }
  private static boolean fits(Dimension d,int n,Map<String,Answer> answers) {
    if(n<=0 || n>MAX_SIZE) return false;
    return switch(d.mode()) { case "FIXED" -> n==d.value(); case "REFERENCE" -> n==value(d.source(),answers); case "RANGE" -> n>=min(d)&&n<=max(d); default -> false; };
  }
  public static List<Field> countFields(Config c,List<Field> fields,Map<String,Answer> preceding,Set<String> hiddenFields) {
    int w,h;try{w=value(c.width(),preceding);h=value(c.height(),preceding);}catch(IllegalArgumentException ex){return fields;}
    List<Field> result=new ArrayList<>();
    for(Field f:fields){int upper=0;
      for(Variant v:c.variants())if(v.fieldKey().equals(f.key()) && available(v,preceding)
          && list(v.conditions()).stream().noneMatch(k -> hiddenFields.contains(k.groupKey()+":"+k.fieldKey())))try{
        int a="RANGE".equals(v.width().mode())?min(v.width()):"FIXED".equals(v.width().mode())?v.width().value():value(v.width().source(),preceding);
        int b="RANGE".equals(v.height().mode())?min(v.height()):"FIXED".equals(v.height().mode())?v.height().value():value(v.height().source(),preceding);
        if(a<=w&&b<=h){double area=(double)a*b*("ELLIPSE".equals(v.shape())?Math.PI/4:1);upper=Math.max(upper,(int)Math.min(MAX_ITEMS,Math.floor((double)w*h/area+1e-9)));}
      }catch(IllegalArgumentException ignored){/* path() reports the missing source. */}
      BigDecimal max=BigDecimal.valueOf(upper);if(f.max()!=null)max=max.min(f.max());
      result.add(ProductStudioConstraints.bound(f,f.min()==null?BigDecimal.ZERO:f.min(),max));
    }
    return result;
  }
  public static List<Ref> references(Config c) {
    List<Ref> refs=new ArrayList<>(); if(c.width()!=null)refs.add(c.width());if(c.height()!=null)refs.add(c.height());
    for(Variant v:list(c.variants()))for(Dimension d:List.of(v.width(),v.height()))if("REFERENCE".equals(d.mode())&&d.source()!=null)refs.add(d.source());
    return refs;
  }
  public static List<String> path(Config c,List<Field> shown,Map<String,Answer> preceding,Set<String> hiddenFields) {
    try {
      int w=value(c.width(),preceding),h=value(c.height(),preceding);
      Set<String> allowed=shown.stream().map(Field::key).collect(Collectors.toSet());
      List<Variant> active=c.variants().stream().filter(v -> allowed.contains(v.fieldKey()) && available(v,preceding)
          && list(v.conditions()).stream().noneMatch(k -> hiddenFields.contains(k.groupKey()+":"+k.fieldKey()))).toList();
      if(active.isEmpty())return List.of("현재 경로에서 생성할 도형이 없습니다. 도형 표시 조건과 입력묶음 분기를 확인해 주세요.");
      boolean possible=false;
      for(Variant v:active) {
        int vw="RANGE".equals(v.width().mode())?min(v.width()):"FIXED".equals(v.width().mode())?v.width().value():value(v.width().source(),preceding);
        int vh="RANGE".equals(v.height().mode())?min(v.height()):"FIXED".equals(v.height().mode())?v.height().value():value(v.height().source(),preceding);
        if(vw<=w && vh<=h)possible=true;
      }
      if(!possible)return List.of("허용된 도형의 최소 치수가 도면보다 큽니다. 도형 치수·앞 사이즈 범위를 확인해 주세요.");
      return List.of();
    } catch(IllegalArgumentException ex){return List.of(ex.getMessage());}
  }
  /** Actual layout is authoritative. Submitted counts and submitted definition snapshots are ignored. */
  public static Answer answer(Process p,Question q,List<Field> shown,Map<String,Answer> preceding,Answer raw,Set<String> hiddenFields) {
    Config c=p.diagrams().get(q.key());
    Layout l=raw.diagram(); require(l!=null,"답변하기를 눌러 도면 배치를 완료해 주세요.");
    require(l.schemaVersion()==1 && c.revision().equals(l.revision()),"도면 설정이 변경되었습니다. 배치를 다시 확인해 주세요.");
    int w=value(c.width(),preceding), h=value(c.height(),preceding);
    require(l.width()==w && l.height()==h,"앞 질문의 가로·높이가 변경되었습니다. 도면을 다시 확인하고 답변을 적용해 주세요.");
    require(l.items()!=null && l.items().size()<=MAX_ITEMS,"배치 도형은 최대 500개입니다.");
    Set<String> allowed=shown.stream().map(Field::key).collect(Collectors.toSet()), ids=new HashSet<>();
    Map<String,Object> counts=new LinkedHashMap<>();shown.forEach(f -> counts.put(f.key(),BigDecimal.ZERO));
    Map<String,Variant> variants=c.variants().stream().collect(Collectors.toMap(Variant::key,x -> x));
    long area=0;
    for(int i=0;i<l.items().size();i++) {
      Placement x=l.items().get(i); require(x!=null,"빈 배치 도형이 포함되어 있습니다.");key(x.id());
      require(ids.add(x.id()),"배치 도형 식별값이 중복됩니다."); Variant v=variants.get(x.variantKey());
      require(v!=null && allowed.contains(v.fieldKey()) && available(v,preceding) && list(v.conditions()).stream().noneMatch(k -> hiddenFields.contains(k.groupKey()+":"+k.fieldKey())),"현재 분기에서 사용할 수 없는 도형이 배치되어 있습니다.");
      require(fits(v.width(),x.width(),preceding) && fits(v.height(),x.height(),preceding),v.name()+": 허용 치수 범위를 확인해 주세요.");
      require(x.x()>=0 && x.y()>=0 && (long)x.x()+x.width()<=w && (long)x.y()+x.height()<=h,v.name()+": 도형이 도면 밖으로 나갑니다.");
      for(int j=0;j<i;j++) require(!overlaps(x,v.shape(),l.items().get(j),variants.get(l.items().get(j).variantKey()).shape()),v.name()+": 다른 도형과 겹칩니다.");
      area+=(long)x.width()*x.height(); counts.put(v.fieldKey(),((BigDecimal)counts.get(v.fieldKey())).add(BigDecimal.ONE));
    }
    if("FILL".equals(c.mode())) require(area==(long)w*h,"도면에 빈 공간이 남아 있습니다. 전체 영역을 채워 주세요.");
    List<Placement> items=l.items().stream().sorted(Comparator.comparing(Placement::id)).toList();
    return new Answer(List.of(),counts,new Layout(1,c.revision(),w,h,items,c));
  }
  public static Config mapConfig(Config c,String revision,java.util.function.Function<String,String> question,
      java.util.function.BiFunction<String,String,String> field,java.util.function.Function<Condition,Condition> condition,String owner) {
    java.util.function.Function<Ref,Ref> ref = r -> r==null?null:new Ref(question.apply(r.questionKey()),field.apply(r.questionKey(),r.fieldKey()));
    java.util.function.Function<Dimension,Dimension> dimension = d -> new Dimension(d.mode(),d.value(),d.min(),d.max(),d.minInclusive(),d.maxInclusive(),ref.apply(d.source()));
    List<Variant> variants=new ArrayList<>();for(int i=0;i<c.variants().size();i++){var v=c.variants().get(i);variants.add(new Variant("V"+i,field.apply(owner,v.fieldKey()),v.name(),v.shape(),dimension.apply(v.width()),dimension.apply(v.height()),v.marks(),list(v.conditions()).stream().map(condition).toList()));}
    Map<String,String> labels=new LinkedHashMap<>();if(c.fieldLabels()!=null)c.fieldLabels().forEach((k,v)->labels.put(field.apply(owner,k),v));
    return new Config(revision,c.mode(),ref.apply(c.width()),ref.apply(c.height()),variants,labels);
  }
  public static Layout remapLayout(Layout layout,Config before,Config after) {
    require(before!=null && after!=null && before.variants().size()==after.variants().size(),"복사할 도면 설정이 일치하지 않습니다. 도면을 다시 시험해 주세요.");
    Map<String,String> variants=new HashMap<>();for(int i=0;i<before.variants().size();i++)variants.put(before.variants().get(i).key(),after.variants().get(i).key());
    List<Placement> items=new ArrayList<>();for(var x:layout.items()){String key=variants.get(x.variantKey());require(key!=null,"복사할 도형 종류가 없습니다.");items.add(new Placement(x.id(),key,x.x(),x.y(),x.width(),x.height()));}
    return new Layout(1,after.revision(),layout.width(),layout.height(),items,after);
  }
  public static Layout canonical(Layout layout) {
    if(layout==null)return null;
    var sorted=list(layout.items()).stream().sorted(Comparator.comparing(Placement::variantKey)
        .thenComparingInt(Placement::x).thenComparingInt(Placement::y).thenComparingInt(Placement::width).thenComparingInt(Placement::height)).toList();
    List<Placement> items=new ArrayList<>();for(int i=0;i<sorted.size();i++){var x=sorted.get(i);items.add(new Placement("P"+i,x.variantKey(),x.x(),x.y(),x.width(),x.height()));}
    return new Layout(layout.schemaVersion(),layout.revision(),layout.width(),layout.height(),items,layout.snapshot());
  }
  /** Axis aligned ellipse intersection by convex quadratic projection. Touching is permitted. */
  public static boolean overlaps(Placement a,String as,Placement b,String bs) {
    if ((long)a.x()+a.width()<=b.x() || (long)b.x()+b.width()<=a.x() || (long)a.y()+a.height()<=b.y() || (long)b.y()+b.height()<=a.y()) return false;
    if("RECT".equals(as)&&"RECT".equals(bs))return true;
    if("RECT".equals(as))return ellipseRect(b,a);
    if("RECT".equals(bs))return ellipseRect(a,b);
    double ax=a.width()/2.0,ay=a.height()/2.0,bx=b.width()/2.0,by=b.height()/2.0;
    double dx=Math.abs(a.x()+ax-b.x()-bx),dy=Math.abs(a.y()+ay-b.y()-by);
    if(dx*dx/(ax*ax)+dy*dy/(ay*ay)<=1)return true;
    double lo=0,hi=1;
    while(projected(dx,dy,ax,ay,bx,by,hi)>1)hi*=2;
    for(int i=0;i<100;i++){double mid=(lo+hi)/2;if(projected(dx,dy,ax,ay,bx,by,mid)>1)lo=mid;else hi=mid;}
    double x=dx/(1+hi*bx*bx/(ax*ax)),y=dy/(1+hi*by*by/(ay*ay));
    return (x-dx)*(x-dx)/(bx*bx)+(y-dy)*(y-dy)/(by*by)<1-1e-12;
  }
  private static double projected(double dx,double dy,double ax,double ay,double bx,double by,double l){
    double x=dx/(1+l*bx*bx/(ax*ax)),y=dy/(1+l*by*by/(ay*ay));return x*x/(ax*ax)+y*y/(ay*ay);
  }
  private static boolean ellipseRect(Placement e,Placement r){
    double a=e.width()/2.0,b=e.height()/2.0,cx=e.x()+a,cy=e.y()+b;
    double dx=cx-Math.max(r.x(),Math.min(cx,(double)r.x()+r.width())),dy=cy-Math.max(r.y(),Math.min(cy,(double)r.y()+r.height()));
    return dx*dx/(a*a)+dy*dy/(b*b)<1-1e-12;
  }
}
