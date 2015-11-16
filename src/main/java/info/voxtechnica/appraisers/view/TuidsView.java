package info.voxtechnica.appraisers.view;

import info.voxtechnica.appraisers.model.Tuid;
import io.dropwizard.views.View;

import java.util.List;

public class TuidsView extends View {
    private final List<Tuid> tuids;

    public TuidsView(List<Tuid> tuids) {
        super("tuids.ftl");
        this.tuids = tuids;
    }

    public List<Tuid> getTuids() {
        return tuids;
    }

}
