package io.github.dadcraftttv.ouralia;
import android.content.Context;
/** In-memory runner fixture, never an audio recording or Android model. */
public class YamNet implements SoundKinds.Model {
    public static int closed;
    public static boolean fail;
    public YamNet(Context c){if(fail)throw new IllegalStateException("test startup failure");}
    public float[] scores(float[] wave){float[] x=new float[521];x[3]=.8f;return x;}
    public void close(){closed++;}
}
