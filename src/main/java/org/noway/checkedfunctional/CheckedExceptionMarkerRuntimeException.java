package org.noway.checkedfunctional;


/**
 * A dedicated runtime exception that lifts a checked exception out of a lambda expression.
 */
public class CheckedExceptionMarkerRuntimeException extends RuntimeException
{
    public CheckedExceptionMarkerRuntimeException()
    {
        super();
    }
    public CheckedExceptionMarkerRuntimeException(Exception e)
    {
        super(e);
    }

    private static final long serialVersionUID = 0L;
}