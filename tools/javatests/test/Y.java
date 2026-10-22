package io.github.dadcraftttv.ouralia;
import java.util.*;
public class Y {   // the room in stereo: StereoRoom's choice, the patch values, the plain room
  static int fails=0;
  static void check(boolean c,String m){ if(!c){fails++;System.out.println("FAIL: "+m);} else System.out.println("ok:   "+m); }
  static StereoRoom.Measure m(int p,boolean opened,boolean same,double corr,double db){ return new StereoRoom.Measure(p,opened,same,corr,db); }
  public static void main(String[] a){
    // the Moto G Stylus 2025, as measured on Oct 22: Unprocessed one mic on both sides, Camcorder two, Mic two but partly opposite
    List<StereoRoom.Measure> moto=List.of(m(StereoRoom.UNPROCESSED,true,true,1,-49),
        m(StereoRoom.CAMCORDER,true,false,0.38,-31), m(StereoRoom.MIC,true,false,-0.16,-31));
    StereoRoom.Choice c=StereoRoom.choose(-56,moto);
    check(c.stereo() && c.preset==StereoRoom.CAMCORDER,"the Moto: Camcorder (Unprocessed is one mic, Mic partly opposite)");
    check(Math.abs(c.trimDb+25)<0.01,"...its level matched to the mono reference: -25 dB ("+c.trimDb+")");
    c=StereoRoom.choose(-40,List.of(m(StereoRoom.UNPROCESSED,true,false,0.5,-42), m(StereoRoom.CAMCORDER,true,false,0.4,-20)));
    check(c.preset==StereoRoom.UNPROCESSED && Math.abs(c.trimDb-2)<0.01,"a phone whose Unprocessed is two mics: that first, trim +2");
    c=StereoRoom.choose(-56,List.of(m(StereoRoom.UNPROCESSED,false,false,0,-120), m(StereoRoom.CAMCORDER,true,true,1,-30), m(StereoRoom.MIC,true,false,-0.3,-30)));
    check(!c.stereo(),"a phone with no two mics in step: mono only");
    c=StereoRoom.choose(-90,List.of(m(StereoRoom.CAMCORDER,true,false,0.2,-10)));
    check(c.trimDb==StereoRoom.MIN_TRIM_DB,"a wild level difference is held within the trim's limits ("+c.trimDb+")");
    float[] off=StereoRoom.patchValues(false,-25);
    check(off[0]==0 && off[1]==1 && off[2]==0,"off: the left channel as it is, no side (the sound as before)");
    float[] on=StereoRoom.patchValues(true,0);
    check(on[0]==1 && Math.abs(on[1]-0.5)<1e-6 && Math.abs(on[2]-0.5)<1e-6,"on at 0 dB: mid and side are half each");
    Map<String,Double> v=new HashMap<>();
    for(String[] r:StereoRoom.RESHAPERS){ if(r[0]!=null) v.put(r[0],1.0); v.put(r[1],0.0); }
    v.put("dist_quality",1.0);
    check(StereoRoom.plain(k->v.getOrDefault(k,0.0))==1f,"no effect reshaping the room: all of it plain");
    check(StereoRoom.extraDelay(k->v.getOrDefault(k,0.0))==0,"Smooth distortion at 0 adds no delay");
    v.put("pitch_mix",0.5); v.put("tune_amount",0.5);
    check(Math.abs(StereoRoom.plain(k->v.getOrDefault(k,0.0))-0.25f)<1e-6,"pitch at half and Sing in Key at half: a quarter plain");
    v.put("pitch_on",0.0);
    check(Math.abs(StereoRoom.plain(k->v.getOrDefault(k,0.0))-0.5f)<1e-6,"...the pitch shifter switched off leaves the room alone");
    v.put("dist_mix",0.3);
    check(StereoRoom.extraDelay(k->v.getOrDefault(k,0.0))==16,"Smooth distortion turned up: the side waits 16 samples");
    v.put("dist_quality",0.0);
    check(StereoRoom.extraDelay(k->v.getOrDefault(k,0.0))==0,"...Classic distortion: none");
    for(String[] r:StereoRoom.RESHAPERS) for(String id:r) if(id!=null) check(Params.byId(id)!=null,"reshaper "+id+" is a setting");
    if(fails==0) System.out.println("ALL PASSED");
  }
}
