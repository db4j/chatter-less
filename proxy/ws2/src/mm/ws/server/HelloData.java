
package mm.ws.server;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class HelloData {

    @SerializedName("server_version")
    @Expose
    public String serverVersion;

    /**
     * No args constructor for use in serialization
     * 
     */
    public HelloData() {
    }

    /**
     * 
     * @param serverVersion
     */
    public HelloData(String serverVersion) {
        super();
        this.serverVersion = serverVersion;
    }

}
