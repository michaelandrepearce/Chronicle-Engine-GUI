package net.openhft.chronicle.engine.gui;

import com.vaadin.annotations.AutoGenerated;
import com.vaadin.annotations.DesignRoot;
import com.vaadin.ui.declarative.Design;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Button;
import com.vaadin.ui.TextField;

/**
 * !! DO NOT EDIT THIS FILE !!
 *
 * This class is generated by Vaadin Designer and will be overwritten.
 *
 * Please make a subclass with logic and additional interfaces as needed,
 * e.g class LoginView extends LoginDesign implements View { }
 */
@DesignRoot
@AutoGenerated
@SuppressWarnings("serial")
public class MapViewUI extends VerticalLayout {
    protected Label entrySubscriberCount;
    protected Label recordCount;
    protected Label keyType;
    protected Label valueType;
    protected Label topicSubscriberCount;
    protected Label keySubscriberCount;
    protected Label path;
    protected Label keyStoreValue;
    protected VerticalLayout gridHolder;
    protected TextField addKey;
    protected TextField addValue;
    protected Button addButton;

    public MapViewUI() {
        Design.read(this);
    }
}
