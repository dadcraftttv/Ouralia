package io.github.dadcraftttv.ouralia;
public class J {   // sound kinds (step 4c): the 16 kHz resampler, the kinds and their hysteresis
  static int fails=0;
  static void check(boolean c,String m){ if(!c){fails++;System.out.println("FAIL: "+m);} else System.out.println("ok:   "+m); }
  static float[] sine(double f,int rate,int n){ float[] x=new float[n]; for(int i=0;i<n;i++) x[i]=(float)Math.sin(2*Math.PI*f*i/rate); return x; }
  static double amp(float[] y,double f){ double c=0,s=0; for(int i=0;i<y.length;i++){ c+=y[i]*Math.cos(2*Math.PI*f*i/16000); s+=y[i]*Math.sin(2*Math.PI*f*i/16000);} return 2*Math.hypot(c,s)/y.length; }
  static float[] sc(int... hot){ float[] s=new float[521]; for(int i=0;i<hot.length;i+=2) s[hot[i]]=hot[i+1]/100f; return s; }
  public static void main(String[] a){
    for(int rate: new int[]{48000,44100}){
      float[] y=SoundKinds.to16k(sine(1000,rate,2*24576),2*24576,rate);
      check(y!=null&&y.length==SoundKinds.SAMPLES&&Math.abs(amp(y,1000)-1)<0.02,"at "+rate+" Hz: 1 kHz comes through whole ("+String.format("%.3f",y==null?0:amp(y,1000))+")");
      float[] z=SoundKinds.to16k(sine(12000,rate,2*24576),2*24576,rate);
      double rms=0; for(float v:z) rms+=v*v; rms=Math.sqrt(rms/z.length);
      check(rms<0.02,"at "+rate+" Hz: 12 kHz doesn't fold down into the 16 kHz copy (rms "+String.format("%.4f",rms)+")");
    }
    float[] r=new float[2*24576]; for(int i=0;i<r.length;i++) r[i]=i;
    float[] y=SoundKinds.to16k(r,r.length,48000);
    check(Math.abs(y[y.length-1]-(r.length-2-32))<1.5&&Math.abs(y[1]-y[0]-3)<1e-3,"the newest samples, 3 to 1 at 48 kHz ("+y[y.length-1]+")");
    check(SoundKinds.to16k(new float[30000],30000,48000)==null,"too little: nothing");
    String kinds="# test\nvoices 0 1\nmusic 2\ntraffic 3\nbirds 4\nwater 5\nsteps 6\nquiet 494\n";
    SoundKinds k=new SoundKinds(kinds);
    check(!k.judge(sc(4,40))&&k.kind()==-1,"one judgement isn't enough");
    check(k.judge(sc(4,40))&&k.kind()==3&&k.heardLine().equals("Heard: birds 0.40"),"two in a row: "+k.heardLine());
    k.judge(sc(1,40,2,30)); check(k.kind()==3,"a new kind needs two too");
    k.judge(sc(1,40,2,30)); check(k.kind()==0,"...then voices, the highest of its classes");
    check(k.judge(sc(4,50))&&k.kind()==3,"a strong lead (0.50) changes it at once");
    k.judge(sc(3,4)); check(k.kind()==3,"quiet always needs two, however sure");
    k.judge(sc(3,4)); k.judge(sc(3,4)); check(k.kind()==SoundKinds.QUIET,"nothing over 0.05: quiet");
    k.judge(sc(3,40,494,90)); k.judge(sc(3,40,494,90)); check(k.kind()==SoundKinds.QUIET,"YAMNet's top class Silence: quiet");
    k.reset(); check(k.kind()==-1&&k.scores()[3]==0,"reset forgets");
    int n=0; while(!k.roomMusic()&&n<200){ k.judge(sc(2,30)); n++; }
    check(n==33,"room music: on after "+n+" judgements of music (32 of 80 named music: the first only proposes it)");
    int q=0; while(k.roomMusic()&&q<200){ k.judge(sc(1,40)); q++; }
    check(q==69,"room music: gone "+q+" judgements after the music stops (the window lets 21 of its 33 go: 12 = 0.15 of 80)");
    k.reset(); for(int i=0;i<200;i++){ k.judge(sc(i%2==0?2:1,40)); } check(!k.roomMusic(),"room music: flipping with voices every judgement stays off (two in a row never names music)");
    k.reset(); for(int i=0;i<400;i++){ k.judge(i%8<4?sc(2,30):sc(1,30)); } check(k.roomMusic(),"room music: half the time music, half voices: on");
    k.reset(); check(!k.roomMusic(),"reset forgets room music");
    int tn=0; while(!k.talking()&&tn<200){ k.judge(sc(0,40)); tn++; }
    check(tn==14,"talk: on after "+tn+" judgements of voices (13 of 32, ~3.5 s: the first only proposes it)");
    int tq=0; while(k.talking()&&tq<200){ k.judge(sc(6,40)); tq++; }
    check(tq==29,"talk: over "+tq+" judgements (~7 s) after the voices stop (the window lets 9 of its 13 go: 4 = 0.15 of 32)");
    k.reset(); for(int i=0;i<400;i++){ k.judge(i%12<8?sc(0,40):sc(6,4)); } check(k.talking(),"talk: speech with pauses stays on");
    k.reset(); check(!k.talking(),"reset forgets talk");
    Param waterSetting=Params.byId("water_trail");
    check(waterSetting!=null&&waterSetting.def==0f&&waterSetting.toggle&&!waterSetting.sendToPd
        &&!waterSetting.inPreset&&!waterSetting.randomizable,"water switch: off, app-controlled, outside presets and dice");
    k.reset(); for(int i=0;i<12;i++) k.judge(sc(5,60));
    check(!k.water(),"water: 12/32 below 40 percent");
    k.judge(sc(5,60)); check(k.water(),"water: 13/32 turns on");
    for(int i=0;i<32;i++) k.judge(sc(5,60));
    for(int i=0;i<27;i++) k.judge(sc(0,60));
    check(k.water(),"water: 5/32 stays on above 15 percent");
    k.judge(sc(0,60)); check(!k.water(),"water: 4/32 turns off");
    for(int i=0;i<32;i++) k.judge(sc(5,60));
    boolean held=true;
    for(int i=0;i<300;i++){ k.judge(sc(i%2==0?5:0,60)); held &= k.water(); }
    check(held,"water: half the window holds on without flapping");
    k.reset(); check(!k.water(),"reset forgets water");
    Param uw=Params.byId("underwater");
    check(uw!=null&&uw.def==0f&&uw.max==2f&&uw.choices!=null&&uw.choices.length==3&&!uw.sendToPd
        &&!uw.inPreset&&!uw.randomizable&&uw.soundKinds,"underwater: Off/Gentle/Deeper, off, app-controlled, outside presets and dice");
    boolean threw=false; try{ new SoundKinds("voices 1\n"); }catch(IllegalArgumentException e){ threw=true; }
    check(threw,"kinds.txt missing a kind is refused");
    check(SoundKinds.costLine(new long[]{3000000,1000000,2000000},3).equals("Sound kinds: 3 judgements, YAMNet median 2.0 ms, slowest 3.0 ms"),"the minute's cost line");
    if(fails==0) System.out.println("ALL PASSED");
  }
}
