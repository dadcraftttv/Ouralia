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
  static String pitches(float[] p) {
    String s=""; for(float n:p) if(n>=0) s+=(int)n+","; return s;
  }
  static String contour(float[] p) {
    String s=""; for(int i=1;i<p.length;i++) s+=(int)(p[i]-p[i-1])+","; return s;
  }
  static void development() {
    boolean bounded=true, varied=true, contours=true, sections=true, fuller=true, repeated=true;
    for(int size:new int[]{5,6,7}) for(int seed=0;seed<16;seed++) {
      CalmBuild.reset(); CalmBuild.scaleSize(size); CalmBuild.dice=new java.util.Random(seed);
      quiet(151); int epoch=CalmBuild.takeReset(); float[] p=CalmBuild.takePhrase();
      java.util.List<String> seq=new java.util.ArrayList<>(), shapes=new java.util.ArrayList<>();
      java.util.List<String> groups=new java.util.ArrayList<>();
      int repeat=0; boolean sparse=false;
      for(int pass=0;pass<64;pass++) {  // 128 bars, only phrases handed to Pd; acknowledge each actual pass
        int notes=0; for(float n:p){ if(n>=0) notes++; bounded &= n==-1 || n>=7 && n<=14 && n==(int)n; }
        bounded &= notes>=3;
        if(sparse) fuller &= notes==6;
        sparse=notes<6;
        String key=pitches(p); seq.add(key); shapes.add(contour(p));
        repeat=pass>0 && key.equals(seq.get(pass-1)) ? repeat+1 : 1;
        varied &= repeat<=3;
        if(pass>=15) varied &= new java.util.HashSet<>(seq.subList(pass-15,pass+1)).size()>=4;
        if(pass%4==1) repeated &= java.util.Arrays.equals(p, lastMotif);
        if(pass%4==0) {
          lastMotif=p.clone();
          if(pass>=8) contours &= !shapes.get(pass).equals(shapes.get(pass-8));
        }
        if(pass%4==3) {
          String group=seq.subList(pass-3,pass+1).toString(); groups.add(group);
          int n=groups.size(); if(n>=3) sections &= !(group.equals(groups.get(n-2)) && group.equals(groups.get(n-3)));
        }
        // A late/stale callback and elapsed time cannot advance the pending phrase.
        CalmBuild.phraseApplied(epoch-1);
        if(CalmBuild.takePhrase()!=null) throw new AssertionError("stale epoch advanced a phrase");
        CalmBuild.phraseApplied(epoch); CalmBuild.phraseApplied(epoch); // duplicate must be harmless
        quiet(4.8); p=CalmBuild.takePhrase();
        if(p==null || CalmBuild.takePhrase()!=null) throw new AssertionError("one next phrase per acknowledgement");
      }
    }
    check(bounded,"development: 48 seeded runs of 128 bars, all scale steps in range, at least three sounding notes");
    check(varied,"development: max three identical passes, four pitch sequences per rolling 32 bars (rests ignored)");
    check(contours && sections,"development: changed contour by 16 bars, no eight-bar section three times");
    check(fuller && repeated,"development: motif repeats once, a spacious pass is followed by six notes");
    check(CalmBuild.octave(9,5)==14 && CalmBuild.octave(11,5)==11 && CalmBuild.octave(14,5)==9
        && CalmBuild.octave(9,7)==9,"octave uses the scale length and falls back without clamping");
    CalmBuild.reset(); CalmBuild.takeReset(); check(CalmBuild.takePhrase()==null,"reset discards pending development");
    quiet(151); int old=CalmBuild.takeReset(); CalmBuild.takePhrase();
    run(1,false,false,-60); int off=CalmBuild.takeReset(); CalmBuild.phraseApplied(old);
    check(off!=old && CalmBuild.takePhrase()==null,"off invalidates the phrase and old acknowledgements");
    quiet(151); old=CalmBuild.takeReset(); CalmBuild.takePhrase();
    CalmBuild.scaleSize(5); Integer changed=CalmBuild.takeReset();
    check(changed!=null && changed!=old && CalmBuild.takePhrase()!=null && CalmBuild.level()==1,
        "live scale change prepares a new phrase without restarting the build");
    noise(4); quiet(16); CalmBuild.takeReset(); CalmBuild.phraseApplied(changed);
    check(CalmBuild.takePhrase()==null,"finished fade invalidates queued notes");
  }
  static float[] lastMotif;
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
    // DadCraft, Nov 4: "it was quiet in my house for several minutes but Calm Builds did nothing". His session log: a
    // steady -56 dBFS room with the Moto's level dropping to about -100 for a moment every 10-20 s. Each dropout pulled
    // the reference down, the room then read as 40 dB of noise, and the wait never finished.
    CalmBuild.reset(); started=false;
    for(int i=0;i<8 && !started;i++){ run(14,true,false,-56f); run(1,true,false,-100f); }
    check(started,"a quiet room with the phone's brief level dropouts still grows the tune (by 2 minutes)");
    CalmBuild.reset(); started=false; quiet(20); run(0.3,true,false,-75f); quiet(60);
    check(started,"a brief real dip doesn't restart the wait");
    CalmBuild.reset(); started=false; run(120,true,true,-60f);
    check(!started,"walking counts as noise");
    CalmBuild.reset(); quiet(40); run(1,false,false,-60f);
    check(CalmBuild.level()==0 && CalmBuild.phase()==CalmBuild.Phase.WAITING,"switch or Music off: gone at once");
    double lo=99, hi=0; CalmBuild.wait01=Math::random;
    for(int i=0;i<200;i++){ CalmBuild.reset(); lo=Math.min(lo,CalmBuild.waitS()); hi=Math.max(hi,CalmBuild.waitS()); }
    check(lo>=30 && hi<=60 && hi-lo>20,"the wait falls anywhere between 30 s and a minute ("+lo+".."+hi+")");
    CalmBuild.wait01=()->0; development();
    System.out.println(fails==0?"ALL PASSED":fails+" FAILED");
    if(fails>0) System.exit(1);
  }
}
