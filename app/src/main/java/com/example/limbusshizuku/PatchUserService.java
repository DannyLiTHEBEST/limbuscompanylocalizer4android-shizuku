package com.example.limbusshizuku;

import android.content.Context;
import android.os.Binder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.Process;
import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.*;

/** Shizuku UserService backend. It performs file access in the service UID/context. */
public final class PatchUserService extends Binder {
  static final int APPLY=0x4c424101;
  private static final String GAME="com.ProjectMoon.LimbusCompany";
  private static final String SUFFIX="/Android/data/"+GAME+"/files/Assets/Resources_moved/Localize/jp";
  private static final String ZIP_PREFIX="Android/data/"+GAME+"/files/Assets/Resources_moved/Localize/jp/";
  private static final String UI="JP_MainUIText.json", KEY="JP_BattleKeywords.json";
  private static final int DESTROY=16777115;
  private final Context context;
  public PatchUserService(){context=null;}
  public PatchUserService(Context c){context=c;}
  @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)throws RemoteException{
    if(code==APPLY){
      String archive=data.readString();String version=data.readString();boolean post=data.readInt()!=0;String ui=data.readString();String key=data.readString();
      try{String result=apply(archive,version,post,ui,key);reply.writeNoException();reply.writeInt(0);reply.writeString(result);}catch(Exception e){reply.writeNoException();reply.writeInt(1);reply.writeString("uid="+Process.myUid()+" context="+readContext()+": "+e.getMessage());}return true;
    }
    if(code==DESTROY){reply.writeNoException();new Thread(()->{try{Thread.sleep(30);}catch(Exception ignored){}System.exit(0);}).start();return true;}
    return super.onTransact(code,data,reply,flags);
  }
  private String readContext(){try{BufferedReader r=new BufferedReader(new FileReader("/proc/self/attr/current"));String s=r.readLine();r.close();return s==null?"?":s;}catch(Exception e){return "?";}}
  private String apply(String archive,String version,boolean post,String expectedUi,String expectedKey)throws Exception{
    if(!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))throw new IOException("bad game version");
    String v=packageVersion();if(!version.equals(v))throw new IOException("game version changed to "+v);
    if(post){if(run("pidof "+GAME+" || true").trim().isEmpty())throw new IOException("game process is not running");if(run("dumpsys activity activities | grep topResumedActivity | tail -1 || true").contains(GAME))throw new IOException("game is still foreground");}
    else if(!run("pidof "+GAME+" || true").trim().isEmpty())throw new IOException("game must be fully stopped before pre-cover");
    Target t=findTarget();if(t==null)throw new IOException("UserService cannot see Android/data in its mount namespace");
    if(!t.dir.canWrite()&&!canWrite(t.dir))throw new IOException("target directory is not writable: "+t.dir);
    File probe=new File(t.dir,".limbus-user-service-probe-"+Process.myPid());if(!probe.createNewFile())throw new IOException("target directory write probe failed");probe.delete();
    InputStream source=null;ZipInputStream zis=null;try{
      File a=new File(archive);if(a.isFile())source=new FileInputStream(a);else if(context!=null)source=context.getAssets().open("jp.zip");else throw new IOException("archive is inaccessible to UserService");
      zis=new ZipInputStream(new BufferedInputStream(source));ZipEntry e;int count=0;long total=0;boolean gotUi=false,gotKey=false;
      while((e=zis.getNextEntry())!=null){if(e.isDirectory())continue;String n=e.getName();if(!n.startsWith(ZIP_PREFIX)||n.contains("..")||n.indexOf('\\')>=0||n.startsWith("/"))throw new IOException("unsafe archive path");String rel=n.substring(ZIP_PREFIX.length());if(rel.isEmpty())continue;File out=new File(t.dir,rel);String canon=out.getCanonicalPath(),base=t.dir.getCanonicalPath()+File.separator;if(!canon.startsWith(base))throw new IOException("archive escapes target");File parent=out.getParentFile();if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IOException("cannot create directory");File part=new File(out.getPath()+".userpart");try(OutputStream os=new BufferedOutputStream(new FileOutputStream(part))){byte[] b=new byte[65536];int nread;while((nread=zis.read(b))!=-1){total+=nread;if(total>160L*1024*1024)throw new IOException("expanded patch too large");os.write(b,0,nread);}}if(out.exists()&&!out.delete())throw new IOException("cannot replace "+rel);if(!part.renameTo(out))throw new IOException("cannot install "+rel);count++;if(rel.equals(UI))gotUi=true;if(rel.equals(KEY))gotKey=true;}
      if(count<1000||!gotUi||!gotKey)throw new IOException("patch content incomplete");
    }finally{if(zis!=null)try{zis.close();}catch(Exception ignored){}else if(source!=null)try{source.close();}catch(Exception ignored){}}
    String h1=sha(new File(t.dir,UI)),h2=sha(new File(t.dir,KEY));if(!expectedUi.equals(h1)||!expectedKey.equals(h2))throw new IOException("key file hash mismatch");return "UserService uid="+Process.myUid()+" target="+t.dir+" verified";
  }
  private static final class Target{final File dir;final String root;Target(File d,String r){dir=d;root=r;}}
  private Target findTarget(){String[] roots={"/mnt/androidwritable/0/emulated","/mnt/installer/0/emulated","/storage/emulated/0","/sdcard","/mnt/pass_through/0/emulated","/mnt/user/0/emulated","/data/media/0"};for(String r:roots){File d=new File(r+SUFFIX);if(d.isDirectory())return new Target(d,r);}return null;}
  private static boolean canWrite(File f){return f.canWrite();}
  private String packageVersion()throws Exception{return run("dumpsys package "+GAME+" | sed -n 's/^[[:space:]]*versionName=//p' | head -n 1").trim();}
  private String run(String command)throws Exception{java.lang.Process p=new ProcessBuilder("/system/bin/sh","-c",command).redirectErrorStream(true).start();ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=p.getInputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1&&out.size()<12000)out.write(b,0,n);}int c=p.waitFor();if(c!=0)throw new IOException(command+" exit="+c);return out.toString("UTF-8");}
  private static String sha(File f)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new BufferedInputStream(new FileInputStream(f))){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}StringBuilder s=new StringBuilder();for(byte x:md.digest())s.append(String.format(Locale.ROOT,"%02x",x&255));return s.toString();}
}
