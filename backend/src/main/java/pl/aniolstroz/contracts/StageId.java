package pl.aniolstroz.contracts;

/** Manipulation stages from the rubric; serialized by name. */
public enum StageId {
    AUTHORITY_CLAIM,
    URGENT_THREAT,
    SECRECY_DEMAND,
    ISOLATION,
    MONEY_REQUEST,
    PAYMENT_CHANNEL,
    REMOTE_ACCESS,
    PERSONAL_DATA_REQUEST,
    /** A word the family asked to be warned about; set by the backend only, never by the model. */
    FAMILY_KEYWORD
}
