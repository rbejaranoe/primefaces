package org.primefaces.renderkit;

import org.primefaces.behavior.confirm.ConfirmBehavior;
import org.primefaces.component.api.AjaxSource;
import org.primefaces.component.api.UIOutcomeTarget;
import org.primefaces.component.menu.Menu;
import org.primefaces.event.MenuActionEvent;
import org.primefaces.model.menu.*;
import org.primefaces.util.ComponentTraversalUtils;

import javax.faces.FacesException;
import javax.faces.component.UIComponent;
import javax.faces.component.UIForm;
import javax.faces.component.behavior.ClientBehavior;
import javax.faces.component.behavior.ClientBehaviorContext;
import javax.faces.component.behavior.ClientBehaviorHolder;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;
import javax.faces.event.PhaseId;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MenuItemHolderRenderer extends OutcomeTargetRenderer {

    private static final String SEPARATOR = "_";

    @Override
    public void decode(FacesContext context, UIComponent component) {
        String clientId = component.getClientId(context);
        Map<String, String> params = context.getExternalContext().getRequestParameterMap();

        String menuid = params.get(clientId + "_menuid");
        if (menuid != null) {
            MenuItem menuitem = findMenuitem(((MenuItemHolder) component).getElements(), menuid);
            MenuActionEvent event = new MenuActionEvent(component, menuitem);

            if (menuitem.isImmediate()) {
                event.setPhaseId(PhaseId.APPLY_REQUEST_VALUES);
            }
            else {
                event.setPhaseId(PhaseId.INVOKE_APPLICATION);
            }

            component.queueEvent(event);
        }
    }

    protected void encodeOnClick(FacesContext context, UIComponent source, MenuItem menuitem) throws IOException {
        ResponseWriter writer = context.getResponseWriter();
        setConfirmationScript(context, menuitem);

        String onclick = menuitem.getOnclick();

        //GET
        if (menuitem.getUrl() != null || menuitem.getOutcome() != null) {
            String targetURL = getTargetURL(context, (UIOutcomeTarget) menuitem);
            writer.writeAttribute("href", targetURL, null);

            if (menuitem.getTarget() != null) {
                writer.writeAttribute("target", menuitem.getTarget(), null);
            }
        }
        //POST
        else {
            writer.writeAttribute("href", "#", null);

            UIForm form = ComponentTraversalUtils.closestForm(context, source);
            if (form == null) {
                throw new FacesException("MenuItem must be inside a form element");
            }

            String command;
            if (menuitem.isDynamic()) {
                String menuClientId = source.getClientId(context);
                Map<String, List<String>> params = menuitem.getParams();
                if (params == null) {
                    params = new LinkedHashMap<>();
                }
                List<String> idParams = Collections.singletonList(menuitem.getId());
                params.put(menuClientId + "_menuid", idParams);

                command = menuitem.isAjax()
                        ? buildAjaxRequest(context, source, (AjaxSource) menuitem, form, params)
                        : buildNonAjaxRequest(context, source, form, menuClientId, params, true);
            }
            else {
                command = menuitem.isAjax()
                        ? buildAjaxRequest(context, (UIComponent & AjaxSource) menuitem, form)
                        : buildNonAjaxRequest(context, ((UIComponent) menuitem), form, ((UIComponent) menuitem).getClientId(context), true);
            }

            onclick = (onclick == null) ? command : onclick + ";" + command;
        }

        if (onclick != null) {
            if (menuitem.requiresConfirmation()) {
                writer.writeAttribute("data-pfconfirmcommand", onclick, null);
                writer.writeAttribute("onclick", menuitem.getConfirmationScript(), "onclick");
            }
            else {
                writer.writeAttribute("onclick", onclick, null);
            }
        }
    }

    protected void encodeSeparator(FacesContext context, Separator separator) throws IOException {
        if (!separator.isRendered()) {
            return;
        }

        ResponseWriter writer = context.getResponseWriter();
        String style = separator.getStyle();
        String styleClass = separator.getStyleClass();
        styleClass = styleClass == null ? Menu.SEPARATOR_CLASS : Menu.SEPARATOR_CLASS + " " + styleClass;

        //title
        writer.startElement("li", null);
        writer.writeAttribute("class", styleClass, null);
        if (style != null) {
            writer.writeAttribute("style", style, null);
        }

        writer.endElement("li");
    }

    protected void setConfirmationScript(FacesContext context, MenuItem item) {
        if (item instanceof ClientBehaviorHolder) {
            Map<String, List<ClientBehavior>> behaviors = ((ClientBehaviorHolder) item).getClientBehaviors();
            List<ClientBehavior> clickBehaviors = (behaviors == null) ? null : behaviors.get("click");

            if (clickBehaviors != null && !clickBehaviors.isEmpty()) {
                for (int i = 0; i < clickBehaviors.size(); i++) {
                    ClientBehavior clientBehavior = clickBehaviors.get(i);
                    if (clientBehavior instanceof ConfirmBehavior) {
                        ClientBehaviorContext cbc = ClientBehaviorContext.createClientBehaviorContext(
                                context, (UIComponent) item, "click", item.getClientId(), Collections.EMPTY_LIST);
                        clientBehavior.getScript(cbc);
                        break;
                    }
                }
            }
        }
    }

    protected MenuItem findMenuitem(List<MenuElement> elements, String id) {
        if (elements == null || elements.isEmpty()) {
            return null;
        }
        else {
            String[] paths = id.split(SEPARATOR);

            if (paths.length == 0) {
                return null;
            }

            int childIndex = Integer.parseInt(paths[0]);
            if (childIndex >= elements.size()) {
                return null;
            }

            MenuElement childElement = elements.get(childIndex);

            if (paths.length == 1) {
                return (MenuItem) childElement;
            }
            else {
                String relativeIndex = id.substring(id.indexOf(SEPARATOR) + 1);

                return findMenuitem(((MenuGroup) childElement).getElements(), relativeIndex);
            }
        }
    }
}
