package io.github.dadcraftttv.ouralia;
public class J {   // sound kinds (step 4c): the 16 kHz resampler, the kinds and their hysteresis
  static int fails=0;
  static void check(boolean c,String m){ if(!c){fails++;System.out.println("FAIL: "+m);} else System.out.println("ok:   "+m); }
  static float[] sine(double f,int rate,int n){ float[] x=new float[n]; for(int i=0;i<n;i++) x[i]=(float)Math.sin(2*Math.PI*f*i/rate); return x; }
  static double amp(float[] y,double f){ double c=0,s=0; for(int i=0;i<y.length;i++){ c+=y[i]*Math.cos(2*Math.PI*f*i/16000); s+=y[i]*Math.sin(2*Math.PI*f*i/16000);} return 2*Math.hypot(c,s)/y.length; }
  static float[] sc(int... hot){ float[] s=new float[521]; for(int i=0;i<hot.length;i+=2) s[hot[i]]=hot[i+1]/100f; return s; }
  /** The resampler as it was before Nov 4, the reference. */
  static float[] oldTo16k(float[] x,int n,int rate){
    double step=(double)rate/16000; int taps=32; double cut=7200.0/rate; float[] h=new float[2*taps+1]; double sum=0;
    for(int i=-taps;i<=taps;i++){ double s=i==0?2*cut:Math.sin(2*Math.PI*cut*i)/(Math.PI*i);
      double w=0.42+0.5*Math.cos(Math.PI*i/(taps+1))+0.08*Math.cos(2*Math.PI*i/(taps+1)); h[i+taps]=(float)(s*w); sum+=s*w; }
    for(int i=0;i<h.length;i++) h[i]/=(float)sum;
    double first=n-2-taps-(SoundKinds.SAMPLES-1)*step; float[] out=new float[SoundKinds.SAMPLES];
    for(int j=0;j<out.length;j++){ double at=first+j*step; int i0=(int)at; double f=at-i0;
      out[j]=(float)((1-f)*fil(x,i0,h,taps)+f*fil(x,i0+1,h,taps)); }
    return out;
  }
  static float fil(float[] x,int i,float[] h,int taps){ float s=0; for(int k=-taps;k<=taps;k++) s+=h[k+taps]*x[i+k]; return s; }
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
    // Review, Nov 4: cached coefficients, a reused buffer and skipping the 0-weight second point change nothing.
    java.util.Random rnd=new java.util.Random(5);
    for(int rate: new int[]{48000,44100}){
      float[] in=new float[2*24576]; for(int i=0;i<in.length;i++) in[i]=(float)rnd.nextGaussian();
      float[] ref=oldTo16k(in,in.length,rate), buf=new float[SoundKinds.SAMPLES];
      float[] got=SoundKinds.to16k(in,in.length,rate,buf), again=SoundKinds.to16k(in,in.length,rate,buf);
      check(got==buf&&java.util.Arrays.equals(ref,got)&&java.util.Arrays.equals(ref,again),
          "at "+rate+" Hz: the reused-buffer resampler matches the old one sample for sample");
    }
    for(int rate: new int[]{48000,44100}){
      int seg=12288, n=SoundKinds.SEGS; float[] stream=new float[20*seg]; for(int i=0;i<stream.length;i++) stream[i]=(float)rnd.nextGaussian();
      SoundKinds.Resampler rs=new SoundKinds.Resampler(); boolean same=true; StringBuilder fr=new StringBuilder();
      int[] ks={0,1,2,3,4,0,1,3,4,0,1};   // consecutive, wrapping, then a skipped judgement (1 -> 3)
      int pos=0;
      for(int i=0;i<ks.length;i++){
        pos+= i==0?0:((ks[i]-ks[i-1]+n)%n)*seg;   // the stream moves on a segment per judgement
        float[] x=java.util.Arrays.copyOfRange(stream,pos,pos+(n-1)*seg);
        float[] want=SoundKinds.to16k(x,x.length,rate), got=rs.next(x,x.length,rate,ks[i],seg);
        same&=java.util.Arrays.equals(want,got); fr.append(rs.fresh).append(i<ks.length-1?",":"");
      }
      String f=fr.toString();
      boolean shape= rate==48000 ? f.equals("15600,4096,4096,4096,4096,4096,4096,15600,4096,4096,4096") : f.replace("15600","").replace(",","").isEmpty();
      check(same&&shape,"at "+rate+" Hz: keeping the last output gives the same samples, computing only the new part ("+f+")");
    }
    check(SoundKinds.costLine(new long[]{3000000,1000000,2000000},new long[]{5000000,4000000,6000000},3).endsWith(
        "; whole judgement median 5.0 ms, slowest 6.0 ms"),"the cost line also gives the whole judgement");
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
    Param stereo=Params.byId("music_stereo");
    check(stereo!=null&&stereo.def==0f&&stereo.toggle&&stereo.sendToPd&&stereo.soundKinds
        &&"music".equals(stereo.section)&&!stereo.inPreset&&!stereo.randomizable,
        "Music in Stereo: Dev Music switch, off, sent to Pd, outside presets and dice");
    Param waterSetting=Params.byId("water_trail");
    check(waterSetting!=null&&waterSetting.def==1f&&"sk_mixed".equals(waterSetting.under)&&waterSetting.toggle&&!waterSetting.sendToPd
        &&waterSetting.inPreset&&!waterSetting.randomizable,"water switch: on under Mixed Surroundings, app-controlled, in presets, outside dice");
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
    Param birdsSetting=Params.byId("birds_lift");
    check(birdsSetting!=null&&birdsSetting.def==1f&&"sk_mixed".equals(birdsSetting.under)&&birdsSetting.toggle&&!birdsSetting.sendToPd
        &&birdsSetting.inPreset&&!birdsSetting.randomizable,"birds switch: on under Mixed Surroundings, app-controlled, in presets, outside dice");
    for(int i=0;i<12;i++) k.judge(sc(4,60));
    check(!k.birds(),"birds: 12/32 below 40 percent");
    k.judge(sc(4,60)); check(k.birds()&&!k.water(),"birds: 13/32 turns on (water stays off)");
    for(int i=0;i<32;i++) k.judge(sc(4,60));
    for(int i=0;i<27;i++) k.judge(sc(6,60));
    check(k.birds(),"birds: 5/32 stays on above 15 percent");
    k.judge(sc(6,60)); check(!k.birds(),"birds: 4/32 turns off");
    held=true; for(int i=0;i<32;i++) k.judge(sc(4,60));
    for(int i=0;i<300;i++){ k.judge(i%3==0?sc(4,60):sc(494,60)); held &= k.birds(); }
    check(held,"birds: calls with quiet gaps (a third of the window) hold on without flapping");
    k.reset(); check(!k.birds(),"reset forgets birds");
    Param uw=Params.byId("underwater");
    check(uw!=null&&uw.def==1f&&uw.max==1f&&uw.toggle&&uw.choices==null&&"sk_mixed".equals(uw.under)&&!uw.sendToPd
        &&uw.inPreset&&!uw.randomizable&&uw.soundKinds,"underwater: a switch under Mixed Surroundings, in presets, outside dice");
    check(uw.clamp(2f)==1f,"a stored Deeper loads as on");
    Param trafficSetting=Params.byId("traffic_pass");
    check(trafficSetting!=null&&trafficSetting.def==1f&&"sk_mixed".equals(trafficSetting.under)&&trafficSetting.toggle&&!trafficSetting.sendToPd
        &&trafficSetting.soundKinds&&trafficSetting.dcs&&"music".equals(trafficSetting.section)&&trafficSetting.inPreset&&!trafficSetting.randomizable,
        "traffic pass: Dev Music sub-switch under Mixed Surroundings, on, in presets, outside dice");
    k.reset(); k.judge(sc(3,19),0); k.judge(sc(3,19),250);
    check(!k.trafficAllowed(),"traffic: 0.19 cannot enter");
    k.judge(sc(3,20),500); check(!k.trafficAllowed(),"traffic: one qualifying lead cannot enter");
    k.judge(sc(3,20),750); check(k.trafficAllowed(),"traffic: two leads at 0.20 enter");
    k.judge(sc(3,10),1000); k.judge(sc(3,10),4000);
    check(k.trafficAllowed(),"traffic: exactly 0.10 holds");
    k.judge(sc(3,9),4250); k.judge(sc(3,9),6249);
    check(k.trafficAllowed(),"traffic: below 0.10 holds for less than two seconds");
    k.judge(sc(3,9),6250); check(!k.trafficAllowed(),"traffic: releases at two seconds");
    k.reset(); k.judge(sc(3,60),0); k.judge(sc(3,60),250);
    k.judge(sc(3,60,4,70),500); k.judge(sc(3,60,4,70),2500);
    check(!k.trafficAllowed(),"traffic: another kind leading for two seconds releases even at high score");
    k.reset(); for(int i=0;i<32;i++) k.judge(sc(0,60),i*250);
    k.judge(sc(3,80),8000); k.judge(sc(3,80),8250);
    check(k.talking()&&!k.trafficAllowed(),"traffic: settled talk has priority without Mind the Gap");
    k.reset(); check(!k.trafficAllowed()&&!k.talking(),"traffic: stop/reset clears permission and talk");
    k.judge(sc(3,80),9000); check(!k.trafficAllowed(),"traffic: restart needs fresh evidence");
    k.judge(sc(3,80),9250); check(k.trafficAllowed(),"traffic: restart enters from two fresh judgements");
    boolean threw=false; try{ new SoundKinds("voices 1\n"); }catch(IllegalArgumentException e){ threw=true; }
    check(threw,"kinds.txt missing a kind is refused");
    check(SoundKinds.costLine(new long[]{3000000,1000000,2000000},3).equals("Sound kinds: 3 judgements, YAMNet median 2.0 ms, slowest 3.0 ms"),"the minute's cost line");
    // Mixed Surroundings (Nov 4, the review's item 3): each kind clearly there counts, not only the leader
    Param mixSetting=Params.byId("sk_mixed");
    check(mixSetting!=null&&mixSetting.def==0f&&mixSetting.toggle&&!mixSetting.sendToPd&&mixSetting.inPreset&&!mixSetting.randomizable,
        "mixed switch: off, app-controlled, in presets, outside dice");
    // The one switch (Nov 5): the seven responses act only under Mixed Surroundings, each with its own sub-switch
    String[] subs={"room_make","voice_gaps","traffic_pass","traffic_flow","water_trail","underwater","birds_lift"};
    java.util.List<String> under=new java.util.ArrayList<>();
    for(Param p:Params.ALL) if("sk_mixed".equals(p.under)) under.add(p.id);
    check(under.equals(java.util.Arrays.asList(subs))||new java.util.HashSet<>(under).equals(new java.util.HashSet<>(java.util.Arrays.asList(subs))),
        "exactly the seven responses sit under Mixed Surroundings: "+under);
    for(String id:subs){ Param p=Params.byId(id); check(p.def==1f&&p.toggle,"sub-switch on by default: "+id); }
    // In presets since Nov 5 ("lets move on to step 3"): a user's preset carries them, built-ins and Realms never move them
    java.util.List<String> mset=new java.util.ArrayList<>(java.util.Arrays.asList(subs)); mset.add("sk_mixed");
    for(String id:mset){ Param p=Params.byId(id);
      check(p.inPreset&&!p.randomizable&&Params.builtInsLeave(id)&&Params.realmsLeave(id)&&Params.mixedSet(id),"in presets, left by built-ins and Realms, not dice: "+id); }
    check(!Params.mixedSet("music_stereo")&&!Params.mixedSet("intensity"),"only Mixed and its seven count as the set");
    for(String bn:PresetManager.builtInNames()) for(String id:mset)
      check(!PresetManager.builtIn(bn).containsKey(id),"built-in preset "+bn+" leaves "+id);
    ParamStore.set("sk_mixed",1); ParamStore.set("water_trail",0);
    PresetManager.soundKinds=true;
    java.util.Map<String,Float> dev;
    try{ dev=PresetManager.parse(PresetManager.toJson("Kitchen",ParamStore.snapshot())).values; }catch(Exception ex){ throw new RuntimeException(ex); }
    check(dev.get("sk_mixed")==1f&&dev.get("water_trail")==0f&&dev.get("birds_lift")!=null,"Dev: a saved preset round-trips Mixed and its seven: "+dev.get("sk_mixed")+" "+dev.get("water_trail"));
    PresetManager.soundKinds=false;
    String rel=PresetManager.toJson("Release",ParamStore.snapshot());
    check(!rel.contains("\"sk_mixed\"")&&!rel.contains("\"water_trail\"")&&!rel.contains("\"music_stereo\""),"without sound kinds a saved preset leaves them out");
    PresetManager.soundKinds=true;
    java.util.Map<String,Float> old=new java.util.HashMap<>(dev); for(String id:mset) old.remove(id);
    ParamStore.set("sk_mixed",1); ParamStore.applyPreset(old);
    check(ParamStore.get("sk_mixed")==1f,"an older preset without them leaves Mixed as it is");
    dev.put("sk_mixed",0f); ParamStore.applyPreset(dev);
    check(ParamStore.get("sk_mixed")==0f&&ParamStore.get("water_trail")==0f,"a preset with them sets them");
    PresetManager.soundKinds=false; ParamStore.set("sk_mixed",0); ParamStore.set("water_trail",1);
    for(boolean mx:new boolean[]{false,true}){
      SoundKinds.mixed=mx; SoundKinds mq=new SoundKinds(kinds);
      // talk by a fountain: voices lead three judgements in four, the water always there under them
      for(int i=0;i<64;i++) mq.judge(i%4==3?sc(0,45,5,50):sc(0,60,5,35));
      boolean talkW=mq.talking(), waterW=mq.water();
      mq.reset();
      // birds beside a road: traffic always loudest, birds clearly there
      for(int i=0;i<96;i++) mq.judge(sc(3,45,4,30));
      boolean birdsR=mq.birds(), roadR=mq.trafficScene();
      if(!mx) check(talkW&&!waterW&&!birdsR&&roadR,"mixed off: as before, only the winner counts (talk "+talkW+", water "+waterW+", birds "+birdsR+", traffic "+roadR+")");
      else check(talkW&&waterW&&birdsR&&roadR,"mixed on: talk by a fountain hears both (talk "+talkW+", water "+waterW+"), birds by a road both (birds "+birdsR+", traffic "+roadR+")");
    }
    SoundKinds.mixed=true; SoundKinds g=new SoundKinds(kinds);
    for(int i=0;i<40;i++) g.judge(sc(5,40,4,35));
    check(g.water()&&g.birds()&&g.waterLeads()&&!g.birdsLead(),"mixed: water and birds both heard, one gesture leads (water), the other waits");
    int turns=0; boolean was=g.waterLeads();
    for(int i=0;i<200;i++){ g.judge(i%2==0?sc(5,40,4,35):sc(5,35,4,40)); if(g.waterLeads()!=was){turns++;was=g.waterLeads();} }
    check(turns==0,"mixed: an even mix doesn't flap between the gestures ("+turns+" changes)");
    for(int i=0;i<40;i++) g.judge(sc(4,60,5,32));
    check(g.birdsLead()&&!g.waterLeads()&&g.water(),"mixed: birds take over once clearly more, water still heard (Underwater keeps it)");
    g.reset(); g.judge(sc(10,3)); g.judge(sc(10,3));
    check(g.heardLine().equals("Heard: unsure")&&g.unsure(),"mixed: nothing scores, not silence: unsure, not quiet ("+g.heardLine()+")");
    g.judge(sc(494,90)); g.judge(sc(494,90));
    check(!g.unsure()&&g.heardLine().startsWith("Heard: quiet"),"mixed: YAMNet's silence is still quiet ("+g.heardLine()+")");
    SoundKinds.mixed=false;
    if(fails==0) System.out.println("ALL PASSED");
  }
}
