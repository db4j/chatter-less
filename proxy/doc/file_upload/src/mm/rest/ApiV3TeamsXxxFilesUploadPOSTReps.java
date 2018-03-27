
package mm.rest;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class ApiV3TeamsXxxFilesUploadPOSTReps {

    @SerializedName("file_infos")
    @Expose
    public List<FileInfo> fileInfos = new ArrayList<FileInfo>();
    @SerializedName("client_ids")
    @Expose
    public List<String> clientIds = new ArrayList<String>();

}
