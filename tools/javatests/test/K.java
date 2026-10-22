package io.github.dadcraftttv.ouralia;
public class K {   // the event log's health line
  static int fails=0;
  static void check(boolean c,String m){ if(!c){fails++;System.out.println("FAIL: "+m);} else System.out.println("ok:   "+m); }
  public static void main(String[] a){
    check(Health.cpuPercent(30_000,60_000)==50 && Health.cpuPercent(90_000,60_000)==150,"CPU in percent of one core (two cores busy: 150)");
    check(Health.cpuPercent(5,0)==-1 && Health.cpuPercent(-1,60_000)==-1,"no stretch or no reading: unknown");
    check(Health.thermal(0).equals("none") && Health.thermal(3).equals("severe") && Health.thermal(9).equals("unknown"),"thermal in a word");
    String l=Health.line(73,false,31.24f,"none",48,0);
    check(l.equals("Health: battery 73% unplugged, 31.2 C, thermal none, app CPU 48% of a core, dropouts 0"),"the full line: "+l);
    l=Health.line(80,true,0,null,-1,-1);
    check(l.equals("Health: battery 80% plugged in"),"what isn't known is left out: "+l);
    if(fails==0) System.out.println("ALL PASSED");
  }
}
