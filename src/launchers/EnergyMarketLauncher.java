package launchers;

import agents.*;

import jade.core.AID;
import jade.core.ProfileImpl;
import jade.wrapper.StaleProxyException;
import recorders.SatisfiedConsumersRecorder;
import sajas.core.Runtime;
import sajas.sim.repast3.Repast3Launcher;
import sajas.wrapper.ContainerController;
import uchicago.src.sim.analysis.DataRecorder;
import uchicago.src.sim.analysis.OpenSequenceGraph;
import uchicago.src.sim.engine.BasicAction;
import uchicago.src.sim.engine.Schedule;
import uchicago.src.sim.engine.SimInit;
import uchicago.src.sim.gui.DisplayConstants;
import uchicago.src.sim.gui.DisplaySurface;
import uchicago.src.sim.gui.Object2DDisplay;
import uchicago.src.sim.space.Object2DGrid;
import utils.EnergyContract;
import utils.EnergyContractProposal;
import utils.GraphicSettings;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;

/**
 * This class represents the world model and is the starting point of the simulation.
 */
public class EnergyMarketLauncher extends Repast3Launcher {

    // Render variables
    private static final Color PRODUCER_COLOR = Color.GREEN;
    private static final Color BROKER_COLOR = Color.YELLOW;
    private static final Color CONSUMER_COLOR = Color.RED;
    private OpenSequenceGraph energyGraphPB = null;
    private OpenSequenceGraph energyGraphBC = null;
    private OpenSequenceGraph energyGraphBA = null;
    private OpenSequenceGraph consumersSatisfied = null;
    private OpenSequenceGraph brokersEnergyWallet = null;
    private OpenSequenceGraph brokersMoneyWallet = null;

    // Logic variables
    private static final int DELAY_SIMULATION = 100;
    private int NUM_PRODUCERS;
    private int NUM_BROKERS;
    private int NUM_CONSUMERS;
    private float MONOPOLY_PROBABILITY;
    private int AVG_DAYS_FOR_AUDIT;

    // Recording variables
    private String LOGS_NAME;
    private Boolean STORE_RECORDS;
    private DataRecorder dataRecorder;

    // Energy Variables
    /**
     * Approximate value of the total energy produced per month in this energy market.
     */
    private static int TOTAL_ENERGY_PRODUCED_PER_MONTH = (int) Math.pow(10, 8); // 1 MWh
    private int actualTotalEnergyProducedPerMonth = 0;

    private ContainerController mainContainer;
    private Random rand;

    private DisplaySurface displaySurface;
    private Object2DGrid world;
    private int density = 10;
    private int worldWidth;
    private int worldHeight;

    private List<Producer> producers;
    private List<Broker> brokers;
    private List<Consumer> consumers;
    private Government government;

    private List<EnergyContract> energyContractsBrokerProducer;
    private List<EnergyContract> energyContractsConsumerBroker;

    private Map<AID, GenericAgent> agents;


    public EnergyMarketLauncher() {
        rand = new Random();

        worldWidth = 40 * density;
        worldHeight = 40 * density;
        DisplayConstants.CELL_WIDTH = 1;
        DisplayConstants.CELL_HEIGHT = 1;

        NUM_PRODUCERS = 50;
        NUM_BROKERS = 5;
        NUM_CONSUMERS = 30;
        MONOPOLY_PROBABILITY = 0.5f;
        AVG_DAYS_FOR_AUDIT = 90;
        LOGS_NAME = "experiment_" + rand.nextInt(10);
        STORE_RECORDS = false;
    }

    public static void main(String[] args) {
        boolean BATCH_MODE = false;

        SimInit init = new SimInit();
        init.setNumRuns(1); // works only in batch mode
        init.loadModel(new EnergyMarketLauncher(), null, BATCH_MODE);
    }

    public void setup() { // called after constructor
        super.setup();

        if (displaySurface != null)
            displaySurface.dispose();

        String surfaceName = "Energy Market - World";

        displaySurface = new DisplaySurface(this, surfaceName);
        registerDisplaySurface(surfaceName, displaySurface);
    }

    public void begin() { // called when "Play" pressed on repast gui
        modelConstructor();
        displayConstructor();
        scheduleConstructor();

        super.begin();
    }


    private void modelConstructor() {
        world = new Object2DGrid(worldWidth, worldHeight);

        producers = new ArrayList<>();
        brokers = new ArrayList<>();
        consumers = new ArrayList<>();

        energyContractsBrokerProducer = new ArrayList<>();
        energyContractsConsumerBroker = new ArrayList<>();

        if (STORE_RECORDS)
            dataRecorderConstructor();
    }

    private void dataRecorderConstructor() {
        dataRecorder = new DataRecorder("./logs/" + LOGS_NAME, this);
        dataRecorder.addNumericDataSource("satisfiedCustomers", new SatisfiedConsumersRecorder(this));
//        dataRecorder.addObjectDataSource("SpaceData", new ObjDataSource());

    }

    private void displayConstructor() {
        Object2DDisplay displayProducers = new Object2DDisplay(world);
        displayProducers.setObjectList(producers);
        displaySurface.addDisplayable(displayProducers, "show producers");

        Object2DDisplay displayBrokers = new Object2DDisplay(world);
        displayBrokers.setObjectList(brokers);
        displaySurface.addDisplayable(displayBrokers, "show brokers");

        Object2DDisplay displayConsumers = new Object2DDisplay(world);
        displayConsumers.setObjectList(consumers);
        displaySurface.addDisplayable(displayConsumers, "show consumers");

        displaySurface.display();

        energyPlotsBuild();

    }

    private void energyPlotsBuild() {
        if (energyGraphPB != null)
            energyGraphPB.dispose();

        energyGraphPB = new OpenSequenceGraph("Energies Producers-Brokers", this);
        energyGraphPB.setAxisTitles("time", "energy");
        addEnergyTrading(energyGraphPB, energyContractsBrokerProducer);
        energyGraphPB.addSequence("Total energy in System", () -> TOTAL_ENERGY_PRODUCED_PER_MONTH);
        energyGraphPB.display();

        if (energyGraphBC != null)
            energyGraphBC.dispose();

        energyGraphBC = new OpenSequenceGraph("Energies Brokers-Consumers", this);
        energyGraphBC.setAxisTitles("time", "energy");
        addEnergyTrading(energyGraphBC, energyContractsConsumerBroker);
        energyGraphBC.addSequence("Total energy in System", () -> TOTAL_ENERGY_PRODUCED_PER_MONTH);
        energyGraphBC.display();

        if (energyGraphBA != null)
            energyGraphBA.dispose();

        energyGraphBA = new OpenSequenceGraph("Brokers Available Energy", this);
        energyGraphBA.setAxisTitles("time", "energy available");
        energyGraphBA.display();

        if (consumersSatisfied != null)
            consumersSatisfied.dispose();

        consumersSatisfied = new OpenSequenceGraph("Consumers Satisfied", this);
        consumersSatisfied.setAxisTitles("time", "consumers");
        consumersSatisfied.addSequence("number", () -> energyContractsConsumerBroker.size());
        consumersSatisfied.display();


        if (brokersEnergyWallet != null)
            brokersEnergyWallet.dispose();

        brokersEnergyWallet = new OpenSequenceGraph("Energy Wallet", this);
        brokersEnergyWallet.setAxisTitles("time", "energy");
        brokersEnergyWallet.display();


        if (brokersMoneyWallet != null)
            brokersMoneyWallet.dispose();

        brokersMoneyWallet = new OpenSequenceGraph("Money Wallet", this);
        brokersMoneyWallet.setAxisTitles("time", "money");
        brokersMoneyWallet.display();
    }

    private void addEnergyTrading(OpenSequenceGraph energyGraph, List<EnergyContract> energyContracts) {
        energyGraph.addSequence("Energy Traded", () -> {
            double energy = 0f;
            for (EnergyContract ec : energyContracts) {
                energy += ec.getEnergyAmountPerCycle();
            }
            return energy;
        });
    }

    private void energyPlotBuildBrokers() {
        for (Broker b : brokers) {
            energyGraphPB.addSequence("Broker: " + b.getLocalName(), () -> {
                double energy = 0f;
                for (EnergyContract ec : energyContractsBrokerProducer) {
                    if (ec.getEnergyClient().getLocalName().equals(b.getLocalName()))
                        energy += ec.getEnergyAmountPerCycle();
                }
                return energy;
            });
        }
    }

    private void energyPlotBuildConsumers() {
        for (Consumer c : consumers) {
            energyGraphBC.addSequence("Consumer: " + c.getLocalName(), () -> {
                double energy = 0f;
                for (EnergyContract ec : energyContractsConsumerBroker) {
                    if (ec.getEnergyClient().getLocalName().equals(c.getLocalName()))
                        energy += ec.getEnergyAmountPerCycle();
                }
                return energy;
            });
        }
    }

    private void energyPlotBrokersAvailable() {
        for (Broker b : brokers) {
            energyGraphBA.addSequence(b.getLocalName(), b::getAvailableMonthlyEnergyQuota);
            brokersEnergyWallet.addSequence(b.getLocalName(), () -> b.getEnergyWallet().getBalance());
            brokersMoneyWallet.addSequence(b.getLocalName(), () -> b.getMoneyWallet().getBalance());
        }
    }

    private void scheduleConstructor() {
        getSchedule().scheduleActionAtInterval(1, this, "simulationStep");
//        getSchedule().scheduleActionAtInterval(1, this, "simulationDelay", Schedule.LAST);
//        getSchedule().scheduleActionAtInterval(1, energyGraphPB, "step", Schedule.LAST);
//        getSchedule().scheduleActionAtInterval(1, energyGraphBC, "step", Schedule.LAST);
//        getSchedule().scheduleActionAtInterval(1, energyGraphBA, "step", Schedule.LAST);
        getSchedule().scheduleActionAtInterval(1, consumersSatisfied, "step", Schedule.LAST);
        getSchedule().scheduleActionAtInterval(1, brokersEnergyWallet, "step", Schedule.LAST);
        getSchedule().scheduleActionAtInterval(1, brokersMoneyWallet, "step", Schedule.LAST);
        getSchedule().scheduleActionAtInterval(1, this, "updateGraph", Schedule.LAST);

        if (STORE_RECORDS) {
            getSchedule().scheduleActionBeginning(0, new BasicAction() {
                public void execute() {
                    dataRecorder.record();
                }
            });

            getSchedule().scheduleActionAtEnd(dataRecorder, "writeToFile");
        }


    }

    public void updateGraph() {
        producers.forEach(GenericAgent::clearContacts);
        brokers.forEach(GenericAgent::clearContacts);
        consumers.forEach(GenericAgent::clearContacts);

        for (EnergyContract ec : energyContractsBrokerProducer) {
            ec.getEnergyClient().addContact(ec.getEnergySupplier());
            ec.getEnergySupplier().addContact(ec.getEnergyClient());
        }

        for (EnergyContract ec : energyContractsConsumerBroker) {
            ec.getEnergyClient().addContact(ec.getEnergySupplier());
            ec.getEnergySupplier().addContact(ec.getEnergyClient());
        }

        displaySurface.updateDisplay();
    }

    @Override
    public String getName() {
        return "AIAD - Electric Market";
    }

    @Override
    public String[] getInitParam() {
        return new String[]{"NUM_PRODUCERS", "NUM_BROKERS", "NUM_CONSUMERS", "MONOPOLY_PROBABILITY",
                "AVG_DAYS_FOR_AUDIT", "LOGS_NAME", "STORE_RECORDS"};
    }

    @Override
    protected void launchJADE() {
        mainContainer = Runtime.instance().createMainContainer(new ProfileImpl());

        try {
            launchAgents();
        } catch (StaleProxyException e) {
            System.err.println("Failed launchAgents in main container.");
            e.printStackTrace();
        }
    }

    private void launchAgents() throws StaleProxyException {
        launchProducers();
        launchBrokers();
        launchConsumers();
        launchGovernment();

        setUpAgentsAIDMap();
    }

    private void setUpAgentsAIDMap() {
        agents = new HashMap<>();

        Stream<GenericAgent> stream = Stream.concat(producers.stream(), brokers.stream());
        stream = Stream.concat(stream, consumers.stream());

        stream.forEach(
                (GenericAgent a) -> agents.put(a.getAID(), a)
        );
    }

    private void launchProducers() throws StaleProxyException {
        for (int i = 0; i < NUM_PRODUCERS; ++i) {
            GraphicSettings gs = makeGraphicsSettings(NUM_PRODUCERS, i, 1, PRODUCER_COLOR);

            int energyLeft = TOTAL_ENERGY_PRODUCED_PER_MONTH - actualTotalEnergyProducedPerMonth;
            int monthlyEnergyQuota = (int) ((energyLeft / (NUM_PRODUCERS - i)) * (0.5 + rand.nextFloat()));
            if (monthlyEnergyQuota > (TOTAL_ENERGY_PRODUCED_PER_MONTH - actualTotalEnergyProducedPerMonth))
                monthlyEnergyQuota = TOTAL_ENERGY_PRODUCED_PER_MONTH - actualTotalEnergyProducedPerMonth;
            actualTotalEnergyProducedPerMonth += monthlyEnergyQuota;

            Producer p = Producer.createProducer(this, gs, monthlyEnergyQuota);
            world.putObjectAt(p.getX(), p.getY(), p);
            producers.add(p);
            mainContainer.acceptNewAgent("producer-" + i, p).start();

            System.out.println(monthlyEnergyQuota);
        }
    }

    private void launchBrokers() throws StaleProxyException {
        int totalEnergyCostPerMonth = producers.parallelStream().mapToInt(
                (Producer p) -> p.getEnergyProductionPerMonth() * p.getEnergyUnitSellPrice()
        ).sum();

        for (int i = 0; i < NUM_BROKERS; ++i) {
            GraphicSettings gs = makeGraphicsSettings(NUM_BROKERS, i, 2, BROKER_COLOR);

            int initialInvestment = (int) ((totalEnergyCostPerMonth / NUM_BROKERS)
                    * (1. + (1. / NUM_BROKERS) * rand.nextFloat()));

            Broker b = new Broker(this, gs, initialInvestment);
            world.putObjectAt(b.getX(), b.getY(), b);
            brokers.add(b);
            mainContainer.acceptNewAgent("broker-" + i, b).start();

        }

        energyPlotBuildBrokers();
        energyPlotBrokersAvailable();
    }

    private void launchConsumers() throws StaleProxyException {
        int totalEnergyConsumed = (int) (actualTotalEnergyProducedPerMonth * 0.90);

        for (int i = 0; i < NUM_CONSUMERS; i++) {
            GraphicSettings gs = makeGraphicsSettings(NUM_CONSUMERS, i, 3, CONSUMER_COLOR);

            int agentConsumption = (int) ((totalEnergyConsumed / (NUM_CONSUMERS - i)) * (0.5 + rand.nextFloat()));
            if (agentConsumption > totalEnergyConsumed)
                agentConsumption = totalEnergyConsumed;
            totalEnergyConsumed -= agentConsumption;

            Consumer c = new Consumer(this, gs, agentConsumption);
            world.putObjectAt(c.getX(), c.getY(), c);
            consumers.add(c);
            mainContainer.acceptNewAgent("consumer-" + i, c).start();
        }

        energyPlotBuildConsumers();
    }

    private void launchGovernment() throws StaleProxyException {
        government = new Government(this, null, MONOPOLY_PROBABILITY);
        mainContainer.acceptNewAgent("government", government).start();
    }

    private GraphicSettings makeGraphicsSettings(int total, int idx, int yCoords, Color color) {
        int x = (worldWidth / (total + 1)) * (idx + 1);
        int y = (int) ((yCoords * worldHeight) / 4 + 20 * (rand.nextFloat() - 0.5f));
        return new GraphicSettings(x, y, color);
    }

    public void simulationDelay() {
        try {
            Thread.sleep(DELAY_SIMULATION);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void simulationStep() {
        producersProduceEnergy();
        consumersConsumeEnergy();

        updateEnergyContracts();

        updateMonopolySearch();
    }

    private void producersProduceEnergy() {
        for (Producer p : producers)
            p.produce();
    }

    private void consumersConsumeEnergy() {
        for (Consumer c : consumers)
            c.consume();
    }

    private void updateEnergyContracts() {

        for (ListIterator<EnergyContract> iter = energyContractsBrokerProducer.listIterator(); iter.hasNext(); ) {
            EnergyContract contract = iter.next();
            if (contract.hasEnded() || !contract.canExchange()) {
                // dealing with Producer's side of the contract
                ((Producer) contract.getEnergySupplier()).setContract(null);
                // dealing with Broker's side of the contract
                ((Broker) contract.getEnergyClient()).getProducerContracts().remove(contract);

                iter.remove();
            } else {
                contract.step();
            }
        }

        for (ListIterator<EnergyContract> iter = energyContractsConsumerBroker.listIterator(); iter.hasNext(); ) {
            EnergyContract contract = iter.next();
            if (contract.hasEnded() || !contract.canExchange()) {
                // dealing with Broker's side of the contract
                ((Broker) contract.getEnergySupplier()).getConsumerContracts().remove(contract);

                // dealing with Consumer's side of the contract
                ((Consumer) contract.getEnergyClient()).setHasBrokerService(false);

                iter.remove();
            } else {
                contract.step();
            }
        }
    }

    private void updateMonopolySearch() {
        if (rand.nextFloat() < ((float) 1 / AVG_DAYS_FOR_AUDIT)) {
            government.breakUpMonopoly();
        }
    }

    public GenericAgent getAgentByAID(AID agentAID) {
        return agents.get(agentAID);
    }

    public void addBrokerProducerContract(EnergyContractProposal contractProposal) {
        Producer supplier = (Producer) getAgentByAID(contractProposal.getEnergySupplierAID());
        Broker client = (Broker) getAgentByAID(contractProposal.getEnergyClientAID());

        EnergyContract contract = new EnergyContract(contractProposal, supplier, client);
        energyContractsBrokerProducer.add(contract);
        contract.step(); // so first month's trades are promptly withdrawn

        client.addEnergyContract(contract);
    }

    public void addConsumerBrokerContract(EnergyContractProposal contractProposal) {
        Broker supplier = (Broker) getAgentByAID(contractProposal.getEnergySupplierAID());
        Consumer client = (Consumer) getAgentByAID(contractProposal.getEnergyClientAID());

        EnergyContract contract = new EnergyContract(contractProposal, supplier, client);
        energyContractsConsumerBroker.add(contract);
        contract.step(); // so first month's trades are promptly withdrawn

        supplier.addConsumerContract(contract);
    }

    public List<Broker> getBrokers() {
        return brokers;
    }

    public List<EnergyContract> getEnergyContractsConsumerBroker() {
        return energyContractsConsumerBroker;
    }

    public int getNUM_PRODUCERS() {
        return NUM_PRODUCERS;
    }

    public void setNUM_PRODUCERS(int NUM_PRODUCERS) throws StaleProxyException {
//        int producersDifference = NUM_PRODUCERS - this.NUM_PRODUCERS;
//        if (producersDifference <= 0){
//            System.out.println("Function Unsupported at the moment.");
//        } else {
//
//            for (int i = 1; i <= producersDifference; ++i){
//                int energyToAdd = (int) ((TOTAL_ENERGY_PRODUCED_PER_MONTH/this.NUM_PRODUCERS) * (0.5 + rand.nextFloat()));
//                TOTAL_ENERGY_PRODUCED_PER_MONTH += energyToAdd;
//                actualTotalEnergyProducedPerMonth += energyToAdd;
//
//                GraphicSettings gs = makeGraphicsSettings(NUM_PRODUCERS, i, 1, PRODUCER_COLOR);
//                Producer p = Producer.createProducer(this, gs, energyToAdd);
//                world.putObjectAt(p.getX(), p.getY(), p);
//                producers.add(p);
//                mainContainer.acceptNewAgent("producer-" + i+this.NUM_PRODUCERS, p).start();
//            }

        this.NUM_PRODUCERS = NUM_PRODUCERS;
//        }


    }

    public int getNUM_BROKERS() {
        return NUM_BROKERS;
    }

    public void setNUM_BROKERS(int NUM_BROKERS) {
        this.NUM_BROKERS = NUM_BROKERS;
    }

    public int getNUM_CONSUMERS() {
        return NUM_CONSUMERS;
    }

    public void setNUM_CONSUMERS(int NUM_CONSUMERS) {
        this.NUM_CONSUMERS = NUM_CONSUMERS;
    }

    public float getMONOPOLY_PROBABILITY() {
        return MONOPOLY_PROBABILITY;
    }

    public void setMONOPOLY_PROBABILITY(float MONOPOLY_PROBABILITY) {
        this.MONOPOLY_PROBABILITY = MONOPOLY_PROBABILITY;
    }

    public int getAVG_DAYS_FOR_AUDIT() {
        return AVG_DAYS_FOR_AUDIT;
    }

    public void setAVG_DAYS_FOR_AUDIT(int AVG_DAYS_FOR_AUDIT) {
        this.AVG_DAYS_FOR_AUDIT = AVG_DAYS_FOR_AUDIT;
    }

    public String getLOGS_NAME() {
        return LOGS_NAME;
    }

    public void setLOGS_NAME(String LOGS_NAME) {
        this.LOGS_NAME = LOGS_NAME;
    }

    public Boolean getSTORE_RECORDS() {
        return STORE_RECORDS;
    }

    public void setSTORE_RECORDS(Boolean STORE_RECORDS) {
        this.STORE_RECORDS = STORE_RECORDS;
    }
}