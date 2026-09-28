package rikka.shizuku;
import moe.shizuku.server.IShizukuService;
/** Bridge for the package-private constructor of ShizukuRemoteProcess. */
public final class RemoteShell {
    private RemoteShell() {}
    public static Process start(String script) throws Exception {
        IShizukuService service=IShizukuService.Stub.asInterface(Shizuku.getBinder());
        if(service==null) throw new java.io.IOException("Shizuku 服务已断开");
        return new ShizukuRemoteProcess(service.newProcess(new String[]{"/system/bin/sh","-c",script}, null, null));
    }
}
