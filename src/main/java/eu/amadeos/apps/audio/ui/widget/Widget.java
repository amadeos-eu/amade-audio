package eu.amadeos.apps.audio.ui.widget;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;

/** Base of the painted widgets: gives them an accessible context (plain JComponents have none). */
public abstract class Widget extends JComponent {
    @Override
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJComponent() {
                @Override
                public AccessibleRole getAccessibleRole() {
                    return role();
                }
            };
        }
        return accessibleContext;
    }

    protected AccessibleRole role() {
        return AccessibleRole.LABEL;
    }
}
