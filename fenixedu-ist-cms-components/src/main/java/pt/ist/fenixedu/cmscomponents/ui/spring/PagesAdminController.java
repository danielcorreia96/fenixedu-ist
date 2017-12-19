/**
 * Copyright © 2013 Instituto Superior Técnico
 *
 * This file is part of FenixEdu IST CMS Components.
 *
 * FenixEdu IST CMS Components is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FenixEdu IST CMS Components is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FenixEdu IST CMS Components.  If not, see <http://www.gnu.org/licenses/>.
 */
package pt.ist.fenixedu.cmscomponents.ui.spring;

import static pt.ist.fenixframework.FenixFramework.getDomainObject;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import org.fenixedu.academic.predicate.AccessControl;
import org.fenixedu.bennu.core.domain.exceptions.BennuCoreDomainException;
import org.fenixedu.bennu.core.security.Authenticate;
import org.fenixedu.bennu.io.domain.GroupBasedFile;
import org.fenixedu.cms.domain.MenuItem;
import org.fenixedu.cms.domain.PermissionEvaluation;
import org.fenixedu.cms.domain.PermissionsArray.Permission;
import org.fenixedu.cms.domain.Site;
import org.fenixedu.cms.exceptions.CmsDomainException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import pt.ist.fenixframework.FenixFramework;
import static org.fenixedu.cms.domain.PermissionEvaluation.ensureCanDoThis;
@RestController
@RequestMapping("/pages/{siteId}/admin")
public class PagesAdminController {

    private static final String JSON_VALUE = "application/json; charset=utf-8";

    @Autowired
    PagesAdminService service;

    @RequestMapping(value = "/data", method = RequestMethod.GET, produces = JSON_VALUE)
    public @ResponseBody String data(@PathVariable String siteId) {
        ensureCanDoThis(site(siteId), Permission.SEE_PAGES, Permission.LIST_MENUS);
        return service.serialize(site(siteId)).toString();
    }


    @RequestMapping(value = "/data/{menuItem}", method = RequestMethod.GET, produces = JSON_VALUE)
    public @ResponseBody String data(@PathVariable String siteId, @PathVariable MenuItem menuItem) {
        ensureCanDoThis(site(siteId), Permission.LIST_MENUS,Permission.SEE_PAGES);
        return service.data(site(siteId), menuItem).toString();
    }

    @RequestMapping(method = RequestMethod.POST, consumes = JSON_VALUE)
    public @ResponseBody String create(@PathVariable String siteId, @RequestBody String bodyJson) {
        PagesAdminBean bean = new PagesAdminBean(bodyJson);
        Site site = site(siteId);
        ensureCanDoThis(site, Permission.CREATE_MENU_ITEM, Permission.CREATE_POST, Permission.CREATE_PAGE);
        if (bean.getParent() != null && bean.getParent().getMenu().getPrivileged()) {
            ensureCanDoThis(site, Permission.EDIT_PRIVILEGED_MENU);
        }
        Optional<MenuItem> menuItem = service.create(site, bean.getParent(), bean.getTitle(), bean.getBody(),bean.getExcerpt(), bean.isVisible());
        return service.serialize(menuItem.get(), true).toString();
    }

    @RequestMapping(value = "/{menuItemId}", method = RequestMethod.DELETE)
    public @ResponseBody String delete(@PathVariable String siteId, @PathVariable String menuItemId) {
        MenuItem item = FenixFramework.getDomainObject(menuItemId);
        ensureCanDoThis(item.getMenu().getSite(), Permission.DELETE_MENU, Permission.EDIT_MENU);
        if (item.getMenu().getPrivileged()) {
            ensureCanDoThis(item.getMenu().getSite(), Permission.DELETE_PRIVILEGED_MENU, Permission.EDIT_PRIVILEGED_MENU);
        }
        service.delete(item);
        return data(siteId);
    }

    @RequestMapping(method = RequestMethod.PUT, consumes = JSON_VALUE)
    public @ResponseBody String edit(@RequestBody String bodyJson) {
        PagesAdminBean bean = new PagesAdminBean(bodyJson);
        ensureCanDoThis(bean.getMenuItem().getMenu().getSite(), Permission.LIST_MENUS, Permission.EDIT_MENU, Permission.EDIT_PAGE, Permission.EDIT_POSTS);
        if (bean.getMenuItem().getMenu().getPrivileged()) {
            ensureCanDoThis(bean.getMenuItem().getMenu().getSite(), Permission.DELETE_PRIVILEGED_MENU, Permission.EDIT_PRIVILEGED_MENU);
        }
        MenuItem menuItem =
                service.edit(bean.getMenuItem(), bean.getTitle(), bean.getBody(), bean.getExcerpt(), bean.getCanViewGroup(), bean.isVisible());
        return service.serialize(menuItem, true).toString();
    }

    @RequestMapping(value = "{menuItemId}/addFile.json", method = RequestMethod.POST)
    public @ResponseBody String addFileJson(@PathVariable("menuItemId") String menuItemId,
            @RequestParam("file") MultipartFile file) throws IOException {
        MenuItem item = FenixFramework.getDomainObject(menuItemId);
        GroupBasedFile addedFile = service.addPostFile(file, item);
        ensureCanDoThis(item.getMenu().getSite(), Permission.LIST_MENUS, Permission.EDIT_POSTS, Permission.EDIT_PAGE);
        return service.describeFile(item.getPage(), addedFile).toString();
    }

    @RequestMapping(value = "/move", method = RequestMethod.PUT, consumes = JSON_VALUE)
    public @ResponseBody String move(@RequestBody String bodyJson) {
        JsonObject json = new JsonParser().parse(bodyJson).getAsJsonObject();
        MenuItem item = getDomainObject(json.get("menuItemId").getAsString());
        MenuItem parent = getDomainObject(json.get("parent").getAsString());
        MenuItem insertAfter =
                getDomainObject(json.get("insertAfter").isJsonNull() ? null : json.get("insertAfter").getAsString());
        ensureCanDoThis(item.getMenu().getSite(), Permission.LIST_MENUS, Permission.EDIT_MENU, Permission.EDIT_MENU_ITEM);
        if (item.getMenu().getPrivileged()
          || (insertAfter!=null && insertAfter.getMenu().getPrivileged())
          || (parent !=null && parent.getMenu().getPrivileged())) {
            ensureCanDoThis(item.getMenu().getSite(), Permission.DELETE_PRIVILEGED_MENU, Permission.EDIT_PRIVILEGED_MENU);
        }
        service.moveTo(item, parent, insertAfter);
        return service.serialize(item, false).toString();
    }

    @RequestMapping(value = "/attachment/{menuItemId}", method = RequestMethod.POST)
    public @ResponseBody String addAttachments(@PathVariable("menuItemId") String menuItemId,
            @RequestParam("file") MultipartFile file) throws IOException {
        MenuItem item = getDomainObject(menuItemId);
        ensureCanDoThis(item.getMenu().getSite(),Permission.LIST_MENUS, Permission.EDIT_POSTS, Permission.EDIT_PAGE);
        service.addAttachment(file.getOriginalFilename(), file, item);
        return getAttachments(menuItemId);
    }

    @RequestMapping(value = "/attachment/{menuItemId}/{fileId}", method = RequestMethod.DELETE, produces = JSON_VALUE)
    public @ResponseBody String deleteAttachments(@PathVariable String menuItemId, @PathVariable String fileId) {
        MenuItem menuItem = getDomainObject(menuItemId);
        GroupBasedFile postFile = getDomainObject(fileId);
        ensureCanDoThis(menuItem.getMenu().getSite(),Permission.LIST_MENUS, Permission.EDIT_POSTS, Permission.EDIT_PAGE);
        service.delete(menuItem, postFile);
        return getAttachments(menuItemId);
    }

    @RequestMapping(value = "/attachments", method = RequestMethod.GET)
    public @ResponseBody String getAttachments(@RequestParam(required = true) String menuItemId) {
        MenuItem menuItem = getDomainObject(menuItemId);
        ensureCanDoThis(menuItem.getMenu().getSite(),Permission.LIST_MENUS, Permission.SEE_PAGES);
        return service.serializeAttachments(menuItem.getPage()).toString();
    }

    @RequestMapping(value = "/attachment", method = RequestMethod.PUT)
    public @ResponseBody String updateAttachment(@RequestBody String bodyJson) {
        JsonObject updateMessage = new JsonParser().parse(bodyJson).getAsJsonObject();
        MenuItem menuItem = getDomainObject(updateMessage.get("menuItemId").getAsString());
        GroupBasedFile attachment = getDomainObject(updateMessage.get("fileId").getAsString());
        ensureCanDoThis(menuItem.getMenu().getSite(),Permission.LIST_MENUS, Permission.EDIT_POSTS, Permission.EDIT_PAGE);
        service.updateAttachment(menuItem, attachment, updateMessage.get("position").getAsInt(),
            updateMessage.get("group").getAsInt(), updateMessage.get("name").getAsString(),
            updateMessage.get("visible").getAsBoolean());
        return getAttachments(menuItem.getExternalId());
    }

    @ModelAttribute("site")
    private Site site(@PathVariable String siteId) {
        Site site = getDomainObject(siteId);
        if (!FenixFramework.isDomainObjectValid(site)) {
            throw BennuCoreDomainException.resourceNotFound(siteId);
        }
        if (site.getExecutionCourse()!=null) {
            if (site.getExecutionCourse().getProfessorshipForCurrentUser() == null) {
                throw CmsDomainException.forbiden();
            }
        } else if (site.getHomepageSite()!=null) {
            if (!Objects.equals(AccessControl.getPerson(), site.getOwner())) {
                throw CmsDomainException.forbiden();
            }
        } else {
            PermissionEvaluation.canAccess(Authenticate.getUser(), site);
        }
        return site;
    }
}
