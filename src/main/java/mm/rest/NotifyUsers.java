
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class NotifyUsers {

    @SerializedName("channel")
    @Expose
    public String channel;
    @SerializedName("desktop")
    @Expose
    public String desktop;
    @SerializedName("desktop_sound")
    @Expose
    public String desktopSound;
    @SerializedName("email")
    @Expose
    public String email;
    @SerializedName("first_name")
    @Expose
    public String firstName;
    @SerializedName("mention_keys")
    @Expose
    public String mentionKeys;
    @SerializedName("push")
    @Expose
    public String push;

}
