
package mm.rest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class TeamsReqs {

    @SerializedName("display_name")
    @Expose
    public String displayName;
    @SerializedName("name")
    @Expose
    public String name;
    @SerializedName("type")
    @Expose
    public String type;

}
