import java.time.LocalDateTime;

public class Estado {
    private LocalDateTime ldt;
    private boolean loadType; //Download = false, Upload = true
    private String description;

    public Estado(LocalDateTime ldt, boolean loadType, String description){
        this.ldt = ldt;
        this.loadType = loadType;
        this.description = description;
    }


}
