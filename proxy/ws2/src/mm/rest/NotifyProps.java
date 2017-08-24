
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class NotifyProps {

    @SerializedName("desktop")
    @Expose
    public String desktop;
    @SerializedName("email")
    @Expose
    public String email;
    @SerializedName("mark_unread")
    @Expose
    public String markUnread;
    @SerializedName("push")
    @Expose
    public String push;

}
