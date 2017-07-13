
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class NotifyUsers {

    @SerializedName("channel")
    @Expose
    public boolean channel;
    @SerializedName("desktop")
    @Expose
    public String desktop;
    @SerializedName("desktop_sound")
    @Expose
    public boolean desktopSound;
    @SerializedName("email")
    @Expose
    public boolean email;
    @SerializedName("first_name")
    @Expose
    public boolean firstName;
    @SerializedName("mention_keys")
    @Expose
    public String mentionKeys;
    @SerializedName("push")
    @Expose
    public String push;

    public NotifyUsers init(UsersReps user) {
        channel = true;
        desktop = "all";
        desktopSound = true;
        email = true;
        mentionKeys = user.username+",@"+user.username;
        return this;
    }

    
}
