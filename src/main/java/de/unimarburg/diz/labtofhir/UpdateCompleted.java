package de.unimarburg.diz.labtofhir;

import org.springframework.context.ApplicationEvent;

public class UpdateCompleted extends ApplicationEvent {

    public UpdateCompleted(Object source) {
        super(source);
    }
}
