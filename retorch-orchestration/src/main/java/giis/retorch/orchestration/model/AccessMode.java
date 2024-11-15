package giis.retorch.orchestration.model;

public class AccessMode {

    private AccessModeTypes type;
    private boolean sharing = false;
    private int concurrency = 1;
    private Resource resource;

    public AccessMode() {}

    public AccessMode(AccessMode type) {
        this.type = type.getType();
        this.concurrency = type.getConcurrency();
        this.resource = type.getResource();
        this.sharing = type.getSharing();
    }

    /**
     * Access mode constructor
     * @param type  {@link AccessModeTypes} that could be READONLY,WRITEONLY,READWRITE,DYNAMIC or NOACCESS
     * @param sharing     Boolean that represents if the resource can be shared or not
     * @param concurrency Integer with the max number of concurrent access
     * @param resource    Resource on which the access mode is performed
     */
    public AccessMode(AccessModeTypes type, boolean sharing, int concurrency, Resource resource) {
        this.type = type;
        this.sharing = sharing;
        this.concurrency = concurrency;
        this.resource = resource;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if ((obj == null) || (!obj.getClass().equals(this.getClass()))) return false;
        AccessMode objToCompare = ((AccessMode) obj);

        return objToCompare.getSharing() == this.sharing && objToCompare.getConcurrency() == this.concurrency
                && objToCompare.getType().equals(this.type) && objToCompare.getResource().equals(this.resource);
    }

    @Override
    public String toString() {
        return "a.m.{" + type + ", " + sharing + ", " + concurrency + ",'" + resource + '\'' + '}';
    }

    public AccessModeTypes getType() {
        return type;
    }
    public int getConcurrency() {
        return concurrency;
    }
    public Resource getResource() {
        return resource;
    }
    public boolean getSharing() {
        return sharing;
    }

    public void setSharing(boolean sharing) {
        this.sharing = sharing;
    }
    public void setResource(Resource resource) {
        this.resource = resource;
    }
    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }
    public void setType(AccessModeTypes type) {
        this.type = type;
    }

}