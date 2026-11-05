package io.github.dadcraftttv.ouralia;
import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import org.puredata.core.PdBase;
/** Runner integration with queued workers: independent listener start, reset, late work and failure. */
public class J2 {
    static int fails;
    static void check(boolean ok,String message){if(!ok){fails++;System.out.println("FAIL: "+message);}}
    static Object field(String name)throws Exception {
        Field f=SoundKindsRunner.class.getDeclaredField(name); f.setAccessible(true); return f.get(null);
    }
    static String lastTraffic() {
        for(int i=PdBase.sent.size()-1;i>=0;i--)if(PdBase.sent.get(i).startsWith("traffic_state="))return PdBase.sent.get(i);
        return "";
    }
    static String lastFlow() {
        for(int i=PdBase.sent.size()-1;i>=0;i--)if(PdBase.sent.get(i).startsWith("traffic_flow_state="))return PdBase.sent.get(i);
        return "";
    }
    static String lastSk() {
        for(int i=PdBase.sent.size()-1;i>=0;i--)if(PdBase.sent.get(i).startsWith("sk_on="))return PdBase.sent.get(i);
        return "";
    }
    static void trafficJudgement() {
        for(int i=0;i<5;i++)SoundKindsRunner.ready(0);
        Handler.drain();
    }
    public static void main(String[] args)throws Exception {
        long[] clock={0}; SoundKindsRunner.clock=()->clock[0]+=SoundKinds.STEP_MS;   // (a judgement every 0.25 s)
        Context c=new Context(new File("/tmp")) {
            public AssetManager getAssets(){return new AssetManager(){
                public InputStream open(String name){return new ByteArrayInputStream(
                    "voices 0\nmusic 2\ntraffic 3\nbirds 4\nwater 5\nsteps 6\nquiet 494\n".getBytes(StandardCharsets.UTF_8));}
            };}
        };
        String[] subs={"room_make","voice_gaps","traffic_pass","traffic_flow","water_trail","underwater","birds_lift"};
        for(String id:subs) ParamStore.set(id,1);
        ParamStore.set("sk_mixed",0);
        check(!SoundKindsRunner.roomEnabled()&&!SoundKindsRunner.gapsEnabled()&&!SoundKindsRunner.trafficEnabled()&&!SoundKindsRunner.flowEnabled()
            &&!SoundKindsRunner.waterEnabled()&&SoundKindsRunner.uwEnabled()==0&&!SoundKindsRunner.birdsEnabled(),"Mixed off: none of the seven act");
        ParamStore.set("sk_mixed",1);
        check(SoundKindsRunner.roomEnabled()&&SoundKindsRunner.gapsEnabled()&&SoundKindsRunner.trafficEnabled()&&SoundKindsRunner.flowEnabled()
            &&SoundKindsRunner.waterEnabled()&&SoundKindsRunner.uwEnabled()==1&&SoundKindsRunner.birdsEnabled(),"Mixed on: all seven act, Underwater on");
        ParamStore.set("room_make",0);
        check(!SoundKindsRunner.roomEnabled()&&SoundKindsRunner.gapsEnabled(),"a sub-switch off turns only that one off");
        for(String id:subs) ParamStore.set(id,0);
        ParamStore.set("sk_mixed",0);
        // the one switch (Nov 5): the traffic responses act only under Mixed Surroundings; the other sub-switches
        // (on by default) off, so only traffic keeps the runner listening
        for(String id:new String[]{"room_make","voice_gaps","traffic_flow","water_trail","underwater","birds_lift"})ParamStore.set(id,0);
        ParamStore.set("sk_mixed",1);
        ParamStore.set("traffic_pass",1); ParamStore.set("music_on",1);
        SoundKindsRunner.attach(c,48000,s->{}); Handler.drain();
        check(SoundKindsRunner.on(),"traffic setting starts listener without Diagnostics");
        trafficJudgement();trafficJudgement();
        check(lastTraffic().equals("traffic_state=2.0"),"runner sends traffic permission on unchanged-kind judgement too");
        SoundKinds ks=(SoundKinds)field("kinds"); float[] voices=new float[521]; voices[0]=.8f;
        for(int i=0;i<32;i++)ks.judge(voices);
        SoundKindsRunner.settingsTick();Handler.drain();
        check(lastTraffic().equals("traffic_state=3.0"),"settled talk reaches patch with Mind the Gap off");
        SoundKindsRunner.detach();
        check(!SoundKindsRunner.on()&&!ks.trafficAllowed()&&!ks.talking(),"stop clears logic immediately before queued cleanup");
        Handler.drain();
        check(lastTraffic().equals("traffic_state=0.0"),"stop sends neutral traffic state");
        SoundKindsRunner.attach(c,48000,s->{});Handler.drain();
        check(SoundKindsRunner.on()&&!((SoundKinds)field("kinds")).trafficAllowed(),"restart has a fresh model and no stale permission");
        trafficJudgement();trafficJudgement();
        ParamStore.set("music_on",0);SoundKindsRunner.settingsTick();Handler.drain();
        check(lastTraffic().equals("traffic_state=0.0")&&!SoundKindsRunner.on()&&lastSk().equals("sk_on=0.0"),
            "Music off stops the listener and its ring (DadCraft, Nov 4)");
        ParamStore.set("music_on",1);SoundKindsRunner.settingsTick();Handler.drain();
        check(SoundKindsRunner.on()&&lastSk().equals("sk_on=1.0"),"Music back on starts it again");
        ParamStore.set("music_on",0);SoundKindsRunner.detach();Handler.drain();
        SoundKindsRunner.attach(c,48000,s->{});Handler.drain();
        check(!SoundKindsRunner.on(),"starting to play with Music off does not listen");
        ParamStore.set("music_on",1);SoundKindsRunner.settingsTick();Handler.drain();
        check(SoundKindsRunner.on(),"then Music on starts it");
        ParamStore.set("traffic_pass",0);SoundKindsRunner.settingsTick();Handler.drain();
        check(!SoundKindsRunner.on(),"last Music listening switch off stops runner");
        // Stop while model creation is queued, then immediately restart: old work must not replace the new model.
        ParamStore.set("traffic_pass",1);ParamStore.set("music_on",1);
        SoundKindsRunner.settingsTick();SoundKindsRunner.detach();
        SoundKindsRunner.attach(c,48000,s->{});Handler.drain();
        check(SoundKindsRunner.on(),"queued old startup/cleanup cannot discard restarted listener");
        // Force a restarted worker's startup ahead of a queued old judgement.
        for(int i=0;i<5;i++)SoundKindsRunner.ready(0);
        SoundKindsRunner.detach();SoundKindsRunner.attach(c,48000,s->{});
        Handler.jobs.remove(Handler.jobs.size()-1).run();Handler.drain();
        check(SoundKindsRunner.on()&&((Integer)field("judged"))==0,
            "old queued judgement cannot infer on the restarted model");
        SoundKindsRunner.detach();Handler.drain();
        // Review finding 2 (Nov 4): every Pd send waits in one queue, in the order its state was read under the lock,
        // so a stop's neutral state always lands after older work. Nothing is sent from the caller's thread.
        ParamStore.set("traffic_pass",1);ParamStore.set("music_on",1);
        SoundKindsRunner.attach(c,48000,s->{});Handler.drain();
        trafficJudgement();trafficJudgement();
        int before=PdBase.sent.size();
        ParamStore.set("music_on",0);SoundKindsRunner.settingsTick();
        check(PdBase.sent.size()==before,"settings changes queue their sends, never send from the caller");
        Handler.drain();ParamStore.set("music_on",1);SoundKindsRunner.settingsTick();Handler.drain();
        // stop during inference: a judgement queued before the stop runs after it and must send nothing
        for(int i=0;i<5;i++)SoundKindsRunner.ready(0);
        SoundKindsRunner.detach();
        before=PdBase.sent.size();Handler.drain();
        check(String.join(",",PdBase.sent.subList(before,PdBase.sent.size())).indexOf("traffic_state=2")<0
            &&lastTraffic().equals("traffic_state=0.0")&&lastSk().equals("sk_on=0.0"),
            "stop during inference: the old judgement sends nothing after the stop's neutral state");
        // stop during loading, no restart: the old startup must not turn the ring back on
        SoundKindsRunner.attach(c,48000,s->{});SoundKindsRunner.detach();Handler.drain();
        check(!SoundKindsRunner.on()&&lastSk().equals("sk_on=0.0"),"stop during loading leaves the ring off: "+lastSk());
        // immediate restart: only the new startup's state remains
        SoundKindsRunner.attach(c,48000,s->{});SoundKindsRunner.detach();SoundKindsRunner.attach(c,48000,s->{});Handler.drain();
        check(SoundKindsRunner.on()&&lastSk().equals("sk_on=1.0"),"stop then restart ends listening with the ring on");
        SoundKindsRunner.detach();Handler.drain();ParamStore.set("traffic_pass",0);
        // Traffic Flow alone: starts the listener, latches the settled scene, leaves pass permission neutral.
        ParamStore.set("traffic_pass",0);ParamStore.set("traffic_flow",1);ParamStore.set("music_on",1);
        SoundKindsRunner.attach(c,48000,s->{});Handler.drain();
        check(SoundKindsRunner.on(),"Traffic Flow alone starts the listener");
        for(int i=0;i<10;i++)trafficJudgement();
        check(lastFlow().equals("traffic_flow_state=1.0"),"no scene before the window fills: "+lastFlow());
        for(int i=0;i<30;i++)trafficJudgement();
        check(lastFlow().equals("traffic_flow_state=2.0")&&lastTraffic().equals("traffic_state=0.0"),
            "settled traffic latches the scene, pass state stays off: "+lastFlow()+" "+lastTraffic());
        SoundKinds kf=(SoundKinds)field("kinds"); float[] quiet=new float[521]; quiet[494]=.8f;
        for(int i=0;i<60;i++)kf.judge(quiet);   // (each STEP_MS after the last: the windows are measured in time)
        check(kf.trafficScene(),"a 15 s lull keeps the scene (red light)");
        for(int i=0;i<20;i++)kf.judge(quiet);
        SoundKindsRunner.settingsTick();Handler.drain();
        check(!kf.trafficScene()&&lastFlow().equals("traffic_flow_state=1.0"),"the scene leaves after the window empties");
        ParamStore.set("traffic_flow",0);SoundKindsRunner.settingsTick();Handler.drain();
        check(!SoundKindsRunner.on()&&lastFlow().equals("traffic_flow_state=0.0"),"Traffic Flow off stops the runner, neutral state");
        SoundKindsRunner.detach();Handler.drain();
        YamNet.fail=true; SoundKindsRunner.attach(c,48000,s->{});Handler.drain();
        check(!SoundKindsRunner.on()&&field("thread")==null,"startup failure clears runner and contribution");
        SoundKindsRunner.detach();
        // Review, Nov 4: the ring copy, oldest whole segment first, in at most two reads (Pd's lock taken at most twice).
        int seg=7, n=SoundKinds.SEGS; PdBase.ring=new float[n*seg]; for(int i=0;i<PdBase.ring.length;i++)PdBase.ring[i]=i;
        boolean order=true; int most=0;
        for(int k=0;k<n;k++){ float[] x=new float[(n-1)*seg]; PdBase.reads=0; SoundKindsRunner.readRing(x,k,seg); most=Math.max(most,PdBase.reads);
            for(int j=1;j<n;j++)for(int i=0;i<seg;i++) if(x[(j-1)*seg+i]!=((k+j)%n)*seg+i) order=false; }
        PdBase.ring=null;
        check(order&&most<=2,"the ring copy keeps the segments' order for every k, in at most "+most+" reads");
        if(fails==0)System.out.println("ALL PASSED"); else System.exit(1);
    }
}
