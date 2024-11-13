package giis.retorch.orchestration.model;

public class AccessModeEntity {

    private AccessModeTypesEntity accessMode;
    private boolean sharing = false;
    private int concurrency = 1;
    private ResourceEntity resource;

    public AccessModeEntity() {}

    public AccessModeEntity(AccessModeEntity accessMode) {
        this.accessMode = accessMode.getAccessMode();
        this.concurrency = accessMode.getConcurrency();
        this.resource = accessMode.getResource();
        this.sharing = accessMode.getSharing();
    }

    /**
     * Access mode constructor
     * @param accessMode  {@link AccessModeTypesEntity} that could be READONLY,WRITEONLY,READWRITE,DYNAMIC or NOACCESS
     * @param sharing     Boolean that represents if the resource can be shared or not
     * @param concurrency Integer with the max number of concurrent access
     * @param resource    Resource on which the access mode is performed
     */
    public AccessModeEntity(AccessModeTypesEntity accessMode, boolean sharing, int concurrency, ResourceEntity resource) {
        this.accessMode = accessMode;
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
        AccessModeEntity objToCompare = ((AccessModeEntity) obj);

        return objToCompare.getSharing() == this.sharing && objToCompare.getConcurrency() == this.concurrency
                && objToCompare.getAccessMode().equals(this.accessMode) && objToCompare.getResource().equals(this.resource);
    }

    @Override
    public String toString() {
        return "a.m.{" + accessMode + ", " + sharing + ", " + concurrency + ",'" + resource + '\'' + '}';
    }

    public AccessModeTypesEntity getAccessMode() {
        return accessMode;
    }
    public int getConcurrency() {
        return concurrency;
    }
    public ResourceEntity getResource() {
        return resource;
    }
    public boolean getSharing() {
        return sharing;
    }

    public void setSharing(boolean sharing) {
        this.sharing = sharing;
    }
    public void setResource(ResourceEntity resource) {
        this.resource = resource;
    }
    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }
    public void setAccessMode(AccessModeTypesEntity accessMode) {
        this.accessMode = accessMode;
    }

}