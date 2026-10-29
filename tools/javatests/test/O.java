package io.github.dadcraftttv.ouralia;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
public class O {   // Session Log: a file per session, only the kept receivers, marks, the switch, the size cap
  static int fails=0;
  static void check(boolean c,String m){ if(!c){fails++;System.out.println("FAIL: "+m);} else System.out.println("ok:   "+m); }
  static String read(File f) throws Exception { return new String(Files.readAllBytes(f.toPath()),"UTF-8"); }
  public static void main(String[] a) throws Exception {
    File tmp=Files.createTempDirectory("sessionlog").toFile();
    SessionLog.setDir(tmp,"test","test");
    SessionLog.second(true,false,false);
    check(!SessionLog.recording(),"off by default: nothing kept");
    ParamStore.set(SessionLog.PARAM,1f);
    SessionLog.second(false,false,false);
    check(!SessionLog.recording(),"switched on but not playing: nothing kept");
    SessionLog.second(true,true,false);
    check(SessionLog.recording(),"switched on and playing: a session starts");
    SessionLog.pd("chime_deg",3f); SessionLog.pd("mic_level",42f); SessionLog.pd("comp_chord",2f,0f,4f,7f,11f,-12f);
    SessionLog.pd("tick16",5f); SessionLog.pd("tick16",0f); SessionLog.pd("heard_off");
    SessionLog.mark("flat \"here\"\n");
    ParamStore.set("music_on",ParamStore.get("music_on")>=0.5f?0f:1f);
    SessionLog.second(true,false,true);
    SessionLog.stopAndWait();
    List<File> s=SessionLog.sessions();
    check(s.size()==1,"one file for the session ("+s.size()+")");
    String text=read(s.get(0)); String[] lines=text.split("\n");
    boolean shaped=true; for(String l:lines) shaped&=l.startsWith("{\"t\":")&&l.endsWith("}");
    check(shaped,"every line is one JSON object ("+lines.length+" lines)");
    check(lines[0].contains("\"ev\":\"start\"")&&lines[0].contains("\"settings\":{")&&lines[lines.length-1].contains("\"ev\":\"stop\""),"starts with the settings, ends with stop");
    check(text.contains("\"src\":\"chime_deg\",\"v\":[3.000]")&&text.contains("\"src\":\"comp_chord\""),"the patch's notes and chords kept");
    check(!text.contains("mic_level"),"what the log doesn't keep is left out");
    int bars=text.split("\"ev\":\"bar\"",-1).length-1;
    check(bars==1,"only the bar line of the 16th-note clock ("+bars+")");
    check(text.contains("\"src\":\"heard_off\",\"v\":[]"),"a bang kept");
    check(text.contains("\"ev\":\"mark\",\"text\":\"flat \\\"here\\\"\\u000a\""),"a mark, quotes and newline escaped");
    check(text.contains("\"ev\":\"set\",\"id\":\"music_on\""),"a changed setting");
    check(text.contains("\"walking\":true")&&text.contains("\"pocket\":true"),"what is heard, once a second");
    SessionLog.second(true,false,false); SessionLog.stopAndWait();
    check(SessionLog.sessions().size()==2,"a second session keeps the first ("+SessionLog.sessions().size()+")");
    SessionLog.second(true,false,false);
    ParamStore.set(SessionLog.PARAM,0f);
    SessionLog.second(true,false,false);
    check(!SessionLog.recording(),"switched off: the session ends");
    SessionLog.stopAndWait();
    long one=SessionLog.sessions().get(0).length();
    SessionLog.maxTotalBytes=one+1;   // (the two kept are each about one: the oldest has to go)
    File oldest=SessionLog.sessions().get(0);
    ParamStore.set(SessionLog.PARAM,1f);
    Thread.sleep(1100);   // (a new second: within the same one, the new file could take the deleted name)
    SessionLog.second(true,false,false); SessionLog.stopAndWait();
    check(!oldest.exists()&&SessionLog.sessions().size()==2,"past the cap the oldest goes, the rest stay ("+SessionLog.sessions().size()+")");
    if(fails==0) System.out.println("ALL PASSED");
  }
}
