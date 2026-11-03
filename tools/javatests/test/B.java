package io.github.dadcraftttv.ouralia;
/** Calm builds, the quiet tune (Nov 3): quiet wait, slow rise, short sounds ignored, steady noise fades over 15 s. */
public class B {
  static int fails=0;
  static void check(boolean c,String m){ if(!c){fails++;System.out.println("FAIL: "+m);} else System.out.println("ok:   "+m); }
  static boolean started, ended;
  static final double DT=0.033;
  static void run(double secs, boolean on, boolean walking, float db){
    for(double s=0;s<secs;s+=DT){ if(CalmBuild.tick(DT,on,walking,db)){ if(CalmBuild.phase()==CalmBuild.Phase.RISING) started=true; else ended=true; } } }
  static void quiet(double secs){ run(secs,true,false,-60f); }
  static void noise(double secs){ run(secs,true,false,-45f); }   // 15 dB over a -60 room, under the guard
  public static void main(String[] a){
    Param cb=Params.byId("calm_build");
    check(cb!=null && cb.def==0f && cb.inPreset && !cb.resetWhenAbsent && !cb.sendToPd,"Calm builds: off by default, in presets (not reset by one without it), app-only");
    CalmBuild.wait01=()->0; CalmBuild.dice=new java.util.Random(7); CalmBuild.reset(); Modulation.reset();
    quiet(29);
    check(CalmBuild.level()==0 && CalmBuild.phase()==CalmBuild.Phase.WAITING && !started,"nothing in the first 30 s of quiet");
    quiet(1.5);
    check(started && CalmBuild.phase()==CalmBuild.Phase.RISING,"then the tune starts");
    float[] ph=CalmBuild.takePhrase();
    boolean inRange=ph!=null && ph.length==CalmBuild.NOTES;
    for(int i=0;inRange && i<ph.length;i++) inRange=ph[i]>=CalmBuild.LOW && ph[i]<=CalmBuild.HIGH;
    check(inRange && ph[0]==ph[CalmBuild.NOTES-1],"a fresh six-note phrase in range that comes home");
    check(CalmBuild.takePhrase()==null,"the phrase is handed over once");
    quiet(60); float half=CalmBuild.level();
    check(half>0.45f && half<0.55f,"it grows slowly ("+half+" after a minute)");
    quiet(61);
    check(CalmBuild.phase()==CalmBuild.Phase.PLAYING && CalmBuild.level()==1f,"after two minutes it plays in full");
    quiet(300);
    check(CalmBuild.phase()==CalmBuild.Phase.PLAYING,"and keeps playing while the room stays quiet");
    for(int i=0;i<5;i++){ noise(0.3); quiet(4); }
    check(CalmBuild.phase()==CalmBuild.Phase.PLAYING,"knocks and coughs (short sounds) change nothing");
    noise(2);
    check(CalmBuild.phase()==CalmBuild.Phase.PLAYING,"two seconds of noise is not yet consistent");
    noise(1.2); ended=false;
    check(CalmBuild.phase()==CalmBuild.Phase.FADING,"three seconds of noise starts the trail-off");
    noise(7.5); float mid=CalmBuild.level();
    check(mid>0.4f && mid<0.6f,"it trails off over 15 s ("+mid+" halfway)");
    quiet(4);
    check(CalmBuild.phase()==CalmBuild.Phase.FADING,"quiet again mid-fade: the fade still finishes");
    quiet(4);
    check(ended && CalmBuild.level()==0 && CalmBuild.phase()==CalmBuild.Phase.WAITING,"then it ends");
    started=false; quiet(25);
    check(!started,"and waits afresh (no quick revival)");
    quiet(6);
    check(started,"...until a full quiet wait has passed");
    // The reference was held while it played: the same noise still counts as noise afterwards.
    noise(4);
    check(CalmBuild.phase()==CalmBuild.Phase.FADING,"held reference: the noise that ended it still ends it");
    // A steady sound above the guard is never quiet; a quieter steady fan becomes the room.
    CalmBuild.reset(); started=false; run(120,true,false,-35f);
    check(!started,"a loud room (over -40 dBFS) never counts as quiet");
    CalmBuild.reset(); started=false; quiet(10); run(110,true,false,-50f);
    check(started,"a steady fan under the guard becomes the room's quiet");
    CalmBuild.reset(); started=false; run(120,true,true,-60f);
    check(!started,"walking counts as noise");
    CalmBuild.reset(); quiet(40); run(1,false,false,-60f);
    check(CalmBuild.level()==0 && CalmBuild.phase()==CalmBuild.Phase.WAITING,"switch or Music off: gone at once");
    double lo=99, hi=0; CalmBuild.wait01=Math::random;
    for(int i=0;i<200;i++){ CalmBuild.reset(); lo=Math.min(lo,CalmBuild.waitS()); hi=Math.max(hi,CalmBuild.waitS()); }
    check(lo>=30 && hi<=60 && hi-lo>20,"the wait falls anywhere between 30 s and a minute ("+lo+".."+hi+")");
    System.out.println(fails==0?"ALL PASSED":fails+" FAILED");
    if(fails>0) System.exit(1);
  }
}
