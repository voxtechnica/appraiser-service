package info.voxtechnica.appraisers.view;

import info.voxtechnica.appraisers.model.Tuid;
import io.dropwizard.views.View;

public class TuidView extends View {
    private final Tuid tuid;

    public TuidView(Tuid tuid) {
        super("tuid.ftl");
        this.tuid = tuid;
    }

    public Tuid getTuid() {
        return tuid;
    }
}
