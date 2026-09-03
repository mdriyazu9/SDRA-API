package sailpoint.community.rest;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Identity;
import sailpoint.rest.plugin.BasePluginResource;
import sailpoint.rest.plugin.RequiredRight;

@RequiredRight("communityRestDelegationResource")
@Path("Object")
public class DelegationResource extends BasePluginResource {

    private static final Log log = LogFactory.getLog(DelegationResource.class);
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd");

    static {
        SDF.setLenient(false);
    }

    @POST
    @Path("setDelegation")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response setDelegation(List<Map<String, Object>> requests) {
        SailPointContext context = null;
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        int successCount = 0;
        int failureCount = 0;

        try {
            if (requests == null || requests.isEmpty()) {
                return Response.status(Status.BAD_REQUEST)
                        .entity("Request body is empty. Provide one or more forwarding entries.")
                        .build();
            }

            context = SailPointFactory.getCurrentContext();

            for (Map<String, Object> req : requests) {
                Map<String, Object> result = new HashMap<String, Object>();

                String identityName = getString(req, "identity");
                String delegationName = getString(req, "delegation");
                String startDateStr = getString(req, "startDate");
                String endDateStr = getString(req, "endDate");

                result.put("identity", identityName);

                try {
                    if (isBlank(identityName) || isBlank(delegationName)) {
                        result.put("status", "FAIL");
                        result.put("message", identityName + " --> fail to set delegation/forwarding");
                        result.put("details", "identity and delegation are required");
                        failureCount++;
                        results.add(result);
                        continue;
                    }

                    Identity identity = context.getObjectByName(Identity.class, identityName);
                    if (identity == null) {
                        result.put("status", "FAIL");
                        result.put("message", identityName + " --> fail to set delegation/forwarding");
                        result.put("details", "Source identity not found");
                        failureCount++;
                        results.add(result);
                        continue;
                    }

                    Identity forwardIdentity = context.getObjectByName(Identity.class, delegationName);
                    if (forwardIdentity == null) {
                        result.put("status", "FAIL");
                        result.put("message", identityName + " --> fail to set delegation/forwarding");
                        result.put("details", "Delegation identity not found");
                        failureCount++;
                        results.add(result);
                        continue;
                    }

                    Date startDate = parseDate(startDateStr);
                    Date endDate = parseDate(endDateStr);

                    Map<String, Object> prefMap = identity.getPreferences();
                    if (prefMap == null) {
                        prefMap = new HashMap<String, Object>();
                    }

                    prefMap.put("forward", forwardIdentity.getName());
                    prefMap.put("forwardStartDate", startDate);
                    prefMap.put("forwardEndDate", endDate);

                    identity.setPreferences(prefMap);
                    context.saveObject(identity);

                    result.put("status", "SUCCESS");
                    result.put("message", identityName + " --> delegation/forwarding set.");
                    result.put("details", "Forwarding set to " + forwardIdentity.getName());
                    successCount++;

                } catch (Exception e) {
                    log.error("Failed processing identity: " + identityName + ". Error: " + e.getMessage(), e);
                    result.put("status", "FAIL");
                    result.put("message", identityName + " --> fail to set delegation/forwarding");
                    result.put("details", e.getMessage());
                    failureCount++;
                }

                results.add(result);
            }

            context.commitTransaction();

            Map<String, Object> response = new HashMap<String, Object>();
            response.put("successCount", Integer.valueOf(successCount));
            response.put("failureCount", Integer.valueOf(failureCount));
            response.put("results", results);

            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("Bulk forwarding update failed: " + e.getMessage(), e);
            if (context != null) {
                try {
                    context.rollbackTransaction();
                } catch (Exception rollbackEx) {
                    log.error("Rollback failed: " + rollbackEx.getMessage(), rollbackEx);
                }
            }
            return Response.serverError().entity(e.getMessage()).build();
        }
    }

    private String getString(Map<String, Object> req, String key) {
        Object value = req.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Date parseDate(String dateStr) throws Exception {
        if (isBlank(dateStr)) {
            return null;
        }
        return SDF.parse(dateStr);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }

    @Override
    public String getPluginName() {
        return "Delegation";
    }
}
