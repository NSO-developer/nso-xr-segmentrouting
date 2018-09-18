package com.tailf.pkg.ipam.exceptions;

/**
 * Thrown when an attempt to create an invalid netmask is made.
 *
 * For a netmask to be valid it must be possible to express as CIDR (Classless
 * Inter Domain Routing) mask, ie when expressed in binary form (
 * b<sub>31</sub> b<sub>30</sub> b<sub>29</sub> &hellip; b<sub>2</sub>
 * b<sub>1</sub> b<sub>0</sub> ) then
 * <style type="text/css">
 * .example {
 *      white-space:pre;
 *      text-align:center;
 * }
 * </style>
 * <div class="example">
 * b<sub><i>n</i></sub> = 0 &rArr; ( &forall; <i>m</i> ; <i>m</i> &lt;
 * <i>n</i> ; b<sub><i>m</i></sub> = 0 )
 * </div>
 */
public class InvalidNetmaskException extends Exception {
    private static final long serialVersionUID = 0;

    public InvalidNetmaskException(String msg) {
        super(msg);
    }
}
