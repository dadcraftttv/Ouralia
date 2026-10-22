package io.github.dadcraftttv.ouralia;
public class A {   // the room listener's hysteresis
  static int fails=0;
  static void check(boolean c,String m){ if(!c){fails++;System.out.println("FAIL: "+m);} else System.out.println("ok:   "+m); }
  static float[] v(float t,float m,float r,float w){ return new float[]{t,m,r,w}; }
  public static void main(String[] a){
    RoomListener l=new RoomListener();
    check(!l.judge(v(0.9f,0,0,0)) && l.kind()==-1,"one judgement isn't enough");
    check(l.judge(v(0.9f,0,0,0)) && l.kind()==RoomListener.TALKING,"two in a row: talking");
    l.judge(v(0.1f,0,0,0)); l.judge(v(0.1f,0,0,0)); l.judge(v(0.1f,0,0,0));
    check(l.kind()==RoomListener.TALKING,"a pause of 1.5 s keeps it");
    check(l.judge(v(0.1f,0,0,0)) && l.kind()==-1,"2 s under: let go");
    l.judge(v(0.6f,0.8f,0,0)); l.judge(v(0.6f,0.8f,0,0));
    check(l.kind()==RoomListener.MUSIC,"the one leading wins: music");
    l.judge(v(0,0,0.9f,0)); check(l.kind()==RoomListener.MUSIC,"a new kind needs two too");
    l.judge(v(0,0,0.9f,0)); check(l.kind()==RoomListener.TRAFFIC,"...then traffic");
    l.reset(); check(l.kind()==-1 && l.scores()[2]==0,"reset forgets");
    if(fails==0) System.out.println("ALL PASSED");
  }
}
