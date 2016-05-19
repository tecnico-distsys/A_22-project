package pt.upa.broker.ws;


import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.jws.HandlerChain;
import javax.jws.WebService;
import javax.xml.registry.JAXRException;



@WebService(
        endpointInterface = "pt.upa.broker.ws.BrokerPortType",
        wsdlLocation = "broker.2_0.wsdl",
        name = "Broker",
        portName = "BrokerPort",
        targetNamespace = "http://ws.broker.upa.pt/",
        serviceName = "BrokerService"
)
@HandlerChain(file = "/broker_handler-chain.xml")
public class MasterBrokerPort extends BrokerPort {
	protected BrokerPortType slave;
	protected String slaveURL;
    protected Timer watchdogTimer = new Timer();
    public MasterBrokerPort(String uddiUrl, String wsName, String masterURL, String slaveURL) throws JAXRException {
    	super(uddiUrl, wsName, masterURL);
    	this.slaveURL = slaveURL;
    	slave = getRemoteBroker(slaveURL);
        watchdogTimer.schedule(new WatchdogTask(), WATCH_DELAY_MS);
    }


    private class WatchdogTask extends TimerTask {
        @Override
        public void run() {
            String res = slave.ping("watchdog");
            watchdogTimer.schedule(new WatchdogTask(), WATCH_DELAY_MS);
        }
    }
    
    @Override
    public String ping(String name) {
    	// Broker state is not changed here
        return super.ping(name);
    }

    @Override
    public String requestTransport(String origin, String destination, int price) throws InvalidPriceFault_Exception,
            UnavailableTransportFault_Exception, UnavailableTransportPriceFault_Exception,
            UnknownLocationFault_Exception {
    	String reqID = getRequestID();
    	String response = getCachedRequest(reqID);
    	if(response == null) {
    		response = super.requestTransport(origin, destination, price);
    	}
    	// cache response and send it to slave
    	cache(reqID, response);
    	sendUpdate(slave, false, response, reqID); // response is the id of the job
    	return response;
    }

    @Override
    public TransportView viewTransport(String id) throws UnknownTransportFault_Exception {
        // NOTE: BrokerTransportView overrides getState() and contacts Transporter if it's necessary
        TransportView tw = super.viewTransport(id);
        // NOTE: (cache response ?) and send it to slave
        sendUpdate(slave, false, id, null); // the TransportView might be modified when getting its state from the Transporter
        return tw;
    }

    @Override
    public List<TransportView> listTransports() {
    	// Broker state is not changed here
        return super.listTransports();
    }

    @Override
    public void clearTransports() {
    	sendUpdate(slave, true, null, null);
        super.clearTransports();
    }

	
}
