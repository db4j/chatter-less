
package mm.rest;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class FilesUploadReps {

    @SerializedName("file_infos")
    @Expose
    public List<FileInfoReps> fileInfos = new ArrayList<FileInfoReps>();
    @SerializedName("client_ids")
    @Expose
    public List<String> clientIds = new ArrayList<String>();

}
