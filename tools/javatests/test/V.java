package app.pdscenes;
// The look (Look.java): any accent, in any theme, keeps every text colour readable on what it sits on.
public class V {
  static int fails=0;
  static void check(boolean c,String m){ if(!c){fails++;System.out.println("FAIL: "+m);} else System.out.println("ok:   "+m); }
  public static void main(String[] a){
    String[] names={"Light","Dark","AMOLED"};
    float[] chromas={0f,0.08f,0.5f,1f};
    int tried=0; double worst=99; String worstAt="";
    for(int t=0;t<3;t++) for(float f:chromas) for(int h=0;h<360;h+=3){
      Look.Palette p=Look.palette(t,h,f);
      int[][] pairs={{p.text,p.bg},{p.text,p.s1},{p.text,p.s2},{p.text,p.dock},{p.text,p.sheet},
        {p.muted,p.s1},{p.muted,p.s2},{p.muted,p.bg},{p.muted,p.sheet},{p.muted,p.dock},
        {p.primaryText,p.s1},{p.primaryText,p.s2},{p.onPrimary,p.primary},{p.onContainer,p.container},{p.err,p.errBg}};
      for(int[] q:pairs){ tried++; double c=Look.contrast(q[0],q[1]);
        if(c<worst){worst=c;worstAt=names[t]+" hue "+h+" chroma "+f;} }
    }
    check(worst>=Look.MIN_TEXT_CONTRAST,"every text colour reads (>= 4.5:1) in "+tried+" pairs; the closest is "
      +String.format("%.2f",worst)+" ("+worstAt+")");
    Look.Palette amo=Look.palette(Look.AMOLED,Look.DEFAULT_HUE,Look.DEFAULT_CHROMA);
    check(amo.bg==0xFF000000,"AMOLED stays true black");
    float[] hc=Look.hueChroma(0xFF8B6CFF);
    check(Math.abs(hc[0]-Look.DEFAULT_HUE)<1.5f && hc[1]>0.95f,"Ouralia violet #8B6CFF is the default hue ("+hc[0]+")");
    check(Look.hsl(0,100,50)==0xFFFF0000 && Look.hsl(120,100,25)==0xFF008000,"hsl() gives the textbook colours");
    Look.Palette grey=Look.palette(Look.DARK,200,0f);
    int r=(grey.s1>>16)&0xFF,g=(grey.s1>>8)&0xFF,b=grey.s1&0xFF;
    check(r==g&&g==b,"colourfulness 0 gives plain greys");
    // the Scale sheet's dots: the same notes the patch plays (make_patch.py SCALES)
    try {
      String py=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(System.getProperty("repo","."),"tools","make_patch.py")));
      String block=py.substring(py.indexOf("SCALES = ["),py.indexOf("]",py.indexOf("SCALES = [")+10));
      java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\(([0-9, ]+)\\)").matcher(block);
      java.util.List<String> fromPy=new java.util.ArrayList<>();
      while(m.find()) fromPy.add(m.group(1).replace(" ",""));
      java.util.List<String> fromJava=new java.util.ArrayList<>();
      for(int[] st:Params.SCALE_STEPS){ StringBuilder sb=new StringBuilder(); for(int x:st){ if(sb.length()>0)sb.append(','); sb.append(x);} fromJava.add(sb.toString()); }
      check(fromPy.equals(fromJava)&&fromJava.size()==Params.byId("music_scale").choices.length,
        "the Scale sheet's dots match the patch's "+fromPy.size()+" scales and the Scale menu");
    } catch(java.io.IOException e){ check(false,"read make_patch.py: "+e); }
    if(fails==0) System.out.println("ALL PASSED");
  }
}
