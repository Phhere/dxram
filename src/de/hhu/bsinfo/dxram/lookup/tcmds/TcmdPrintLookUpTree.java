
package de.hhu.bsinfo.dxram.lookup.tcmds;

public class TcmdPrintLookUpTree {

	//	private static final Argument MS_ARG_NID =
	//			new Argument("cid", null, false, "Full Node ID of the node to read Look Up Tree from");
	//
	//	@Override
	//	public String getName() {
	//
	//		return "lookuptree";
	//	}
	//
	//	@Override
	//	public String getDescription() {
	//
	//		return "prints the look up tree of a specified node";
	//	}
	//
	//	@Override
	//	public void registerArguments(ArgumentList p_arguments) {
	//
	//		p_arguments.setArgument(MS_ARG_NID);
	//	}
	//
	//	@Override
	//	public boolean execute(ArgumentList p_arguments) {
	//
	//		Short nid = p_arguments.getArgumentValue(MS_ARG_NID, Short.class);
	//
	//		short responsibleSuperPeer = -1;
	//		LookupTree lookupTree = null;
	//
	//		if (nid == null)
	//			return false;
	//
	//		LookupService lookupService = getTerminalDelegate().getDXRAMService(LookupService.class);
	//
	//		if (lookupService == null) {
	//			System.out.println("Lookupservice is null");
	//		} else {
	//			responsibleSuperPeer = lookupService.getResponsibleSuperpeer(nid);
	//		}
	//
	//		if (responsibleSuperPeer != -1) {
	//			lookupTree = lookupService.getLookupTreeFromSuperPeer(responsibleSuperPeer, nid);
	//		}
	//
	//		System.out.println("LookupTree:\n" + lookupTree);
	//
	//		return true;
	//	}

}