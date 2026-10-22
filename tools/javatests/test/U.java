package io.github.dadcraftttv.ouralia;
public class U {   // tap tempo
  static int fails=0;
  static void check(boolean c,String m){ if(!c){fails++;System.out.println("FAIL: "+m);} else System.out.println("ok:   "+m); }
  public static void main(String[] a){
    TapTempo t=new TapTempo();
    check(t.tap(1000)==0f,"one tap: nothing yet");
    check(t.tap(1600)==100f,"two taps 600 ms apart: 100 BPM");
    float b=0; long at=1600; long[] wob={590,610,600,620,580};
    for(long g:wob){ at+=g; b=t.tap(at); }
    check(b==100f,"uneven taps average out: "+b);
    at+=3000; check(t.tap(at)==0f && t.taps()==1,"a pause starts over");
    at+=500; check(t.tap(at)==120f,"500 ms: 120");
    at+=1000; float d=t.tap(at);
    check(d==60f,"a gap twice as long (a missed tap) starts over from it: "+d);
    t=new TapTempo(); t.tap(0); b=t.tap(300);
    check(b==100f,"300 ms (200 BPM) folds down an octave: "+b);
    check(TapTempo.fold(45f)==90f && TapTempo.fold(160f)==160f && TapTempo.fold(99.6f)==100f,"folding into 60-160, whole BPM");
    Param p=Params.byId("tempo_bpm");
    check(p!=null && p.min==TapTempo.MIN_BPM && p.max==TapTempo.MAX_BPM,"the range is Set Tempo's");
    if(fails==0) System.out.println("ALL PASSED");
  }
}
