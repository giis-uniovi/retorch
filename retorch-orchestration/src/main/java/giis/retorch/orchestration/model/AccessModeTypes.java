package giis.retorch.orchestration.model;

/**
 * This class is used as AccessMode  parser for given a string representation of a RETORCH access mode (i.e:
 * READONLY,READWRITE...) convert it into the proper enumeration. Also provides
 */
public class AccessModeTypes {

    public enum type {READONLY, READWRITE, WRITEONLY, DYNAMIC, NOACCESS}

    private final String accessStringType;

    private type accessModeType;

    public AccessModeTypes(String typeOfAccess) {
        this.accessStringType = typeOfAccess;
        switch (typeOfAccess) {
            case "READONLY":
                this.accessModeType = type.READONLY;
                break;
            case "READWRITE":
                this.accessModeType = type.READWRITE;
                break;
            case "WRITEONLY":
                this.accessModeType = type.WRITEONLY;
                break;
            case "DYNAMIC":
                this.accessModeType = type.DYNAMIC;
                break;
            case "NOACCESS":
                this.accessModeType = type.NOACCESS;
                break;
            default:
                break;
        }
    }
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        return this.accessStringType;
    }

    @Override
    public boolean equals(Object obj) {
        if ((obj == null)||(!obj.getClass().equals(this.getClass()))) return false;
        AccessModeTypes currentType = ((AccessModeTypes) obj);
        return currentType.getAccessModeType() == this.getAccessModeType();
    }

    public type getAccessModeType() {
        return accessModeType;
    }

    /**
     * Support method that checks if the given String is valid access mode
     * @param accessMode String with the access mode: READONLY,READWRITE,WRITEONLY,DYNAMIC or NOACESS
     */
    public static boolean isValidAccessMode(String accessMode) {
        return accessMode.equals("READONLY") || accessMode.equals("READWRITE") || accessMode.equals("WRITEONLY")
                || accessMode.equals("DYNAMIC") || accessMode.equals("NOACCESS");
    }
}