package com.thinkbiganalytics.jira;


import com.google.common.base.Predicate;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Collections2;
import com.thinkbiganalytics.rest.JerseyClientException;
import com.thinkbiganalytics.rest.JerseyRestClient;
import com.thinkbiganalytics.jira.domain.*;
import com.thinkbiganalytics.rest.JodaTimeMapperProvider;
import org.apache.commons.lang3.StringUtils;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Created by sr186054 on 10/15/15.
 */
public class JiraJerseyClient extends JerseyRestClient implements JiraClient{

    private String apiPath = "/rest/api/latest/";

    LoadingCache<String, List<String>> issueTypeNameCache;

    public JiraJerseyClient(JiraRestClientConfig config) {
        super(config);
        this.apiPath = config.getApiPath();

// cache the Issue Types related to a project since they will not change much.
// But if they do, expire the cache after a certain amount of time

      this.issueTypeNameCache = CacheBuilder.newBuilder()
                .expireAfterAccess(20, TimeUnit.MINUTES)
                .build(
                        new CacheLoader<String, List<String>>() {
                            public List<String> load(String key) {
                                try {
                                    return loadIssueTypeNamesForProject(key);
                                } catch (JiraException e) {
                                    return null;
                                }
                            }
                        });
        if(client != null){
            client.register(JodaTimeMapperProvider.class);
        }
    }

    protected WebTarget getBaseTarget() {
        WebTarget target = super.getBaseTarget();
        return target.path(apiPath);
    }

    public Issue getIssue(String key) throws JiraException {
        try {
            GetIssue getIssue = get("issue/" + key, null, GetIssue.class);
            Issue issue = new Issue(getIssue);
            return issue;
        } catch (JerseyClientException e) {
            throw new JiraException("Error getting Issue: " + key, e);
        }

    }

    /***
     * Return the JIRA User that is allowed to be assigned issues for a given Project and UserName
     *
     * @param projectKey
     * @param username
     * @return
     * @throws JerseyClientException
     */
    public User getAssignableUser(String projectKey, String username) throws JerseyClientException {

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("project", projectKey);
        params.put("username", username);

        List<User> users = get("/user/assignable/search", params, new GenericType<List<User>>() {
        });
        if (users != null && !users.isEmpty()) {
            return users.get(0);
        }
        return null;
    }

    /**
     * Check to see if a user is allowed to be assigned issues for a given project
     *
     * @param projectKey
     * @param username
     * @return
     */
    public boolean isAssignable(String projectKey, String username) {
        User user = null;
        try {
            user = getAssignableUser(projectKey, username);
        } catch (JerseyClientException e) {
        }
        return user != null;
    }

    /**
     * Return the CreateMeta Schema that needs to used to create new Jira Issues
     *
     * @param projectKey
     * @return
     * @throws JiraException
     */
    public CreateMeta getCreateMetadata(String projectKey) throws JiraException {

        try {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("projectKeys", projectKey);
            params.put("expand", "projects.issuetypes.fields");
            String str = get("/issue/createmeta", params, String.class);

            CreateMeta createData = get("/issue/createmeta", params, CreateMeta.class);
            return createData;
        } catch (JerseyClientException e) {
            throw new JiraException("Error getting Create Metadata for Project " + projectKey, e);
        }
    }

    /**
     * Return the list of valid IssueTypes for a given Jira Project
     *
     * @param projectKey
     * @return
     * @throws JiraException
     */
    public List<IssueType> getIssueTypesForProject(String projectKey) throws JiraException {
        CreateMeta createData = getCreateMetadata(projectKey);
        Project project = createData.getProject();
        if (project != null) {
            return project.getIssueTypes();
        } else {
            return null;
        }
    }

    /**
     * Return the List of Issue Type Names for a given Project
     *
     * @param projectKey
     * @return
     * @throws JiraException
     */
    private List<String> loadIssueTypeNamesForProject(String projectKey) throws JiraException {
            List<IssueType> issueTypes = null;
            issueTypes = getIssueTypesForProject(projectKey);
            List<String> names = new ArrayList<>();
            if (issueTypes != null) {
                for (IssueType issueType : issueTypes) {
                    names.add(issueType.getName());
                }
            }
        return names;
    }


    /**
     * return the List of String names of the IssueTypes for a given Project
     * @param projectKey
     * @return
     */
    public List<String>getIssueTypeNamesForProject(String projectKey){
        return issueTypeNameCache.getUnchecked(projectKey);
    }


    /**
     * Check to see if a given issue type is valid for a Project
     *
     * @param projectKey
     * @param issueTypeName
     * @return
     * @throws JiraException
     */
    public boolean isValidIssueType(String projectKey, final String issueTypeName) {

            List<String> issueTypes  = getIssueTypeNamesForProject(projectKey);

       return isValidIssueType(issueTypes, issueTypeName);

    }

    /**
     * check gto see if the issueType List has the passed in issueTypeName
     * @param issueTypes
     * @param issueTypeName
     * @return
     */
    public boolean isValidIssueType(List<String> issueTypes, final String issueTypeName ) {

        if (issueTypes != null) {
            Predicate<String> matchesProject = new Predicate<String>() {
                @Override
                public boolean apply(String issueType) {
                    return issueType.equals(issueTypeName);
                }
            };
            Collection<String> matchingTypes = Collections2.filter(issueTypes, matchesProject);
            return matchingTypes != null && !matchingTypes.isEmpty();
        }
        return false;

    }


    /**
     * Create a new Jira Issue
     * @param issue
     * @return
     * @throws JiraException
     */
    public Issue createIssue(Issue issue) throws JiraException {


        String projectKey = issue.getProject() != null ? issue.getProject().getKey() : null;
        String issueType = issue.getIssueType() != null ? issue.getIssueType().getName() : null;
        String summary = issue.getSummary();
        String description = issue.getDescription();
        String assigneeName = issue.getAssignee() != null ? issue.getAssignee().getName() : null;


        //Validate the parameters
        List<String> issueTypes = getIssueTypeNamesForProject(projectKey);

        //Validate the Project Name
        if (issueTypes == null) {
            throw new JiraException("Unable to Create Issue: Project " + projectKey + " does not exist.  Issue Details are: " + issue);
        }
        boolean validIssueType = isValidIssueType(issueTypes,issueType);
        if (!validIssueType) {
            //set it to the first one??
            throw new JiraException("Unable to Create Issue: Issue type " + issueType + " is not allowed for Project " + projectKey + ".  Valid Issue Types are: " + issueTypes + ". Issue Details are:" + issue);
        }

        //Validate the Assignee
        if(StringUtils.isBlank(assigneeName)){
            //default it to the current Rest client user name
            assigneeName = super.getUsername();
        }

        boolean assignable = isAssignable(projectKey, assigneeName);
        if (!assignable) {
            throw new JiraException("Unable to Create Issue: User " + assigneeName + " is not allowed to be assigned issues for Project " + projectKey + ". Issue Details are:" + issue);
        }

        //Validate required fields
        if(StringUtils.isBlank(summary)){
            throw new JiraException("Unable to Create Issue: Summary is required");
        }
        if(StringUtils.isBlank(description)){
            throw new JiraException("Unable to Create Issue: Description is required");
        }

        try {
            //Transform it to a CreateIssue
            CreateIssue createIssue = new CreateIssue(issue);

            //Post it
            GetIssue response = post("/issue/", createIssue, GetIssue.class);
            //transform the result back to a populated issue
            issue = getIssue(response.getKey());
            LOG.info("Created JIRA Issue {}, - {}",issue.getKey(),issue.getSummary());

        } catch (JerseyClientException e) {
            String message = "Error Creating Issue " + issue;
            throw new JiraException(message, e);
        }
        return issue;
    }


    /**
     * Create a new Jira Issue
     *
     * @param projectKey
     * @param summary
     * @param description
     * @param issueType
     * @param assigneeName
     * @return
     * @throws JiraException
     */
    public Issue createIssue(String projectKey, String summary, String description, String issueType, String assigneeName) throws JiraException {
        //validate issuetype before creating
        Issue issue = new IssueBuilder(projectKey,issueType).setSummary(summary).setDescription(description).setAssignee(assigneeName).build();
        return createIssue(issue);

    }

    public ServerInfo getServerInfo() throws JiraException {
        ServerInfo serverInfo = null;
        try {
       serverInfo = get("/serverInfo",null,ServerInfo.class);

        } catch (JerseyClientException e) {
            String message = "Error getting serverinfo "+e.getMessage();
            throw new JiraException(message, e);
        }
        return serverInfo;
    }



}