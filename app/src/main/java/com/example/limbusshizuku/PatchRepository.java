package com.example.limbusshizuku;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Version-matched translation bundle; all network operations run off the UI thread. */
final class PatchRepository {
  static final String REPO="https://github.com/pzwboy/LocalizeLimbusCompanyForAndroid";
  static final String PREFIX="Android/data/com.ProjectMoon.LimbusCompany/files/Assets/Resources_moved/Localize/jp/";
  static final String BUILTIN_VERSION="1.115.0";
  static final String BUILTIN_SHA="71053bca7a40bf83b0150bc95e70843d67f84b47bfa86b926de7368f3c4fff7c";
  private static final long LIMIT=45L*1024*1024;
  static class Patch { final File file; final String version,sha,ui,keywords,source;
    Patch(File f,String v,String s,String u,String k,String src){file=f;version=v;sha=s;ui=u;keywords=k;source=src;}
  }
  private final Context ctx;
  PatchRepository(Context context){ctx=context.getApplicationContext();}
  static String sha(InputStream stream)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] b=new byte[65536];int n;while((n=stream.read(b))!=-1)md.update(b,0,n);StringBuilder s=new StringBuilder();for(byte v:md.digest())s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();}
  static String sha(File f)throws Exception{try(InputStream in=new FileInputStream(f)){return sha(in);}}
  private static boolean validHost(URL u){String h=u.getHost().toLowerCase(Locale.ROOT);return "github.com".equals(h)||h.endsWith(".githubusercontent.com")||"githubusercontent.com".equals(h);}
  private static HttpURLConnection open(String address)throws Exception{
    URL url=new URL(address);
    for(int i=0;i<7;i++){
      if(!"https".equalsIgnoreCase(url.getProtocol())||!validHost(url))throw new IOException("不可信的补丁下载地址");
      HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(9000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","Limbus-Shizuku-Helper/1.0.2");
      int code=c.getResponseCode();if(code==301||code==302||code==303||code==307||code==308){String loc=c.getHeaderField("Location");if(loc==null)throw new IOException("下载跳转缺失");URL next=new URL(url,loc);c.disconnect();url=next;continue;}return c;
    }
    throw new IOException("下载跳转次数过多");
  }
  String latestTag()throws Exception{
    HttpURLConnection c=open(REPO+"/releases/latest");try{
      if(c.getResponseCode()!=200)throw new IOException("无法获取上游最新版 (HTTP "+c.getResponseCode()+")");
      String path=c.getURL().getPath(), marker="/releases/tag/v";int p=path.indexOf(marker);if(p<0)throw new IOException("发布页没有正式版标签");
      String v=path.substring(p+marker.length());if(!v.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))throw new IOException("无法识别最新正式版号");return v;
    }finally{c.disconnect();}
  }
  Patch bundled(String version)throws Exception{
    if(!BUILTIN_VERSION.equals(version))throw new IOException("内置补丁版本 "+BUILTIN_VERSION+" 与游戏 "+version+" 不匹配");
    File f=new File(ctx.getFilesDir(),"jp-"+version+"-bundled.zip");if(!f.isFile()||!BUILTIN_SHA.equals(sha(f))){File part=new File(ctx.getFilesDir(),"bundled.part");
      try(InputStream in=ctx.getAssets().open("jp.zip");OutputStream out=new FileOutputStream(part)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
      if(!BUILTIN_SHA.equals(sha(part))){part.delete();throw new IOException("内置资源损坏");}if(f.exists()&&!f.delete())throw new IOException("无法更新内置缓存");if(!part.renameTo(f))throw new IOException("无法保存内置缓存");
    }
    return inspect(f,version,"内置备用");
  }
  Patch cached(String version)throws Exception{
    File f=new File(ctx.getFilesDir(),"jp-"+version+"-online.zip");if(!f.isFile())return null;
    try{return inspect(f,version,"已下载缓存");}catch(Exception e){f.delete();return null;}
  }
  Patch download(String version)throws Exception{
    if(!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+"))throw new IOException("游戏版本号格式不支持");
    String url=REPO+"/releases/download/v"+version+"/jp.zip";
    HttpURLConnection c=open(url);File part=new File(ctx.getFilesDir(),"jp-download.part");long count=0;
    try{
      if(c.getResponseCode()!=200)throw new IOException("未找到与游戏 "+version+" 匹配的正式 jp.zip (HTTP "+c.getResponseCode()+")");
      try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(part)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){count+=n;if(count>LIMIT)throw new IOException("补丁超出大小上限");out.write(b,0,n);}}
      if(count<100000)throw new IOException("下载内容不完整");
      Patch checked=inspect(part,version,"GitHub 最新匹配版");
      File target=new File(ctx.getFilesDir(),"jp-"+version+"-online.zip");
      if(target.isFile() && checked.sha.equals(sha(target))){part.delete();return new Patch(target,version,checked.sha,checked.ui,checked.keywords,"已是最新在线补丁");}
      File backup=new File(ctx.getFilesDir(),"jp-"+version+"-previous.zip");
      if(backup.exists()&&!backup.delete())throw new IOException("无法备份原缓存");
      boolean hadOld=target.exists();if(hadOld&&!target.renameTo(backup))throw new IOException("无法保护原缓存");
      if(!part.renameTo(target)){if(hadOld)backup.renameTo(target);throw new IOException("保存补丁失败，原缓存已保留");}
      if(hadOld)backup.delete();
      return new Patch(target,version,checked.sha,checked.ui,checked.keywords,"GitHub 最新匹配版");
    }finally{c.disconnect();part.delete();}
  }
  Patch inspect(File f,String version,String source)throws Exception{
    String full=sha(f),ui=null,keywords=null;int count=0;long expanded=0;Set<String> seen=new HashSet<String>();
    try(ZipFile zip=new ZipFile(f)){
      Enumeration<? extends ZipEntry> it=zip.entries();while(it.hasMoreElements()){
        ZipEntry e=it.nextElement();String n=e.getName();
        if(n.startsWith("/")||n.contains("..")||n.indexOf('\\')>=0||!seen.add(n))throw new IOException("补丁包含不安全或重复路径");
        if(e.isDirectory()){
          if(!PREFIX.startsWith(n)&&!n.startsWith(PREFIX))throw new IOException("补丁包含额外目录");
          continue;
        }
        if(!n.startsWith(PREFIX))throw new IOException("补丁包含非日语文件");
        if(!n.endsWith(".json")||e.getSize()>3*1024*1024||e.getSize()<0)throw new IOException("补丁文件格式或大小异常");
        count++;expanded+=e.getSize();if(count>6000||expanded>150L*1024*1024)throw new IOException("补丁文件数量或解压大小异常");
        if(n.equals(PREFIX+"JP_MainUIText.json")||n.equals(PREFIX+"JP_BattleKeywords.json")){
          byte[] bytes=readEntry(zip,e);String json=new String(bytes,"UTF-8").trim();if(!json.startsWith("{")||!json.contains("\"dataList\""))throw new IOException("关键翻译 JSON 无 dataList");
          String h=sha(new ByteArrayInputStream(bytes));if(n.endsWith("JP_MainUIText.json"))ui=h;else keywords=h;
        }
      }
    }
    if(count<1000||ui==null||keywords==null)throw new IOException("补丁缺少关键文件或文件数不足");
    if("内置备用".equals(source)&&!BUILTIN_SHA.equals(full))throw new IOException("内置文件哈希不符");
    return new Patch(f,version,full,ui,keywords,source);
  }
  private static byte[] readEntry(ZipFile zip,ZipEntry e)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();try(InputStream in=zip.getInputStream(e)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(o.size()+n>3*1024*1024)throw new IOException("翻译 JSON 过大");o.write(b,0,n);}}return o.toByteArray();}
}
