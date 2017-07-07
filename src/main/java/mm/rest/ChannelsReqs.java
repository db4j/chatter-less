
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ChannelsReqs {

    @SerializedName("team_id")
    @Expose
    public String teamId;
    @SerializedName("name")
    @Expose
    public String name;
    @SerializedName("display_name")
    @Expose
    public String displayName;
    @SerializedName("purpose")
    @Expose
    public String purpose;
    @SerializedName("header")
    @Expose
    public String header;
    @SerializedName("type")
    @Expose
    public String type;

}
