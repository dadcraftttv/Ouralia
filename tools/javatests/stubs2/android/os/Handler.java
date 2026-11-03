package android.os;
import java.util.*;
public class Handler {
  public static final List<Runnable> jobs=new ArrayList<>();
  public Handler(Object looper){}
  public boolean post(Runnable r){jobs.add(r);return true;}
  public static void drain(){while(!jobs.isEmpty())jobs.remove(0).run();}
}
