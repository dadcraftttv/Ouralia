package io.github.dadcraftttv.ouralia;
public class Z {   // sleep fade: the timer's gain and the minutes it shows
  static int fails=0;
  static void check(boolean c,String m){ if(!c){fails++;System.out.println("FAIL: "+m);} else System.out.println("ok:   "+m); }
  static double db(float g){ return 20*Math.log10(g); }
  public static void main(String[] a){
    check(SleepTimer.gain(30*60_000L)==1f && SleepTimer.gain(SleepTimer.FADE_MS)==1f,"full level until the fade");
    double half=db(SleepTimer.gain(SleepTimer.FADE_MS/2));
    check(Math.abs(half+SleepTimer.FADE_DB/2)<0.01,"halfway through the fade: half the dB ("+half+")");
    double end=db(SleepTimer.gain(1));
    check(end<-SleepTimer.FADE_DB+0.1,"near the end: down "+SleepTimer.FADE_DB+" dB, then stopped ("+end+")");
    check(SleepTimer.gain(0)==0f && SleepTimer.gain(-5)==0f,"time up: silence");
    float last=2f; boolean down=true;
    for(long t=SleepTimer.FADE_MS;t>=0;t-=1000){ float g=SleepTimer.gain(t); down&=g<=last; last=g; }
    check(down,"the fade only goes down");
    check(SleepTimer.minutesLeft(15*60_000L)==15 && SleepTimer.minutesLeft(60_001)==2 && SleepTimer.minutesLeft(1)==1
        && SleepTimer.minutesLeft(0)==0,"minutes left round up");
    check(SleepTimer.MINUTES[0]==0,"the first choice is Off");
    if(fails==0) System.out.println("ALL PASSED");
  }
}
