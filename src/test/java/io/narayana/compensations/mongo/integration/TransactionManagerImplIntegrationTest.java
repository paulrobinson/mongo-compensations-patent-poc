package io.narayana.compensations.mongo.integration;

import io.narayana.compensations.mongo.SystemException;
import io.narayana.compensations.mongo.TransactionManager;
import io.narayana.compensations.mongo.WrongStateException;
import io.narayana.compensations.mongo.common.DeploymentHelper;
import io.narayana.compensations.mongo.dummy.DummyCompensationAction;
import io.narayana.compensations.mongo.dummy.DummyConfirmationAction;
import io.narayana.compensations.mongo.dummy.DummyState;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.logging.Logger;
import org.jboss.narayana.compensations.impl.BAControler;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;

/**
 * @author <a href="mailto:gytis@redhat.com">Gytis Trikleris</a>
 */
@RunWith(Arquillian.class)
public class TransactionManagerImplIntegrationTest {

    private static final Logger LOGGER = Logger.getLogger(TransactionManagerImplIntegrationTest.class);

    @Inject
    private TransactionManager transactionManager;

    @Inject
    private BAControler baController;

    @Deployment
    public static Archive<?> getDeployment() {
        final JavaArchive archive = DeploymentHelper.getJavaArchive()
                .addClasses(DeploymentHelper.getBaseClasses())
                .addClasses(DeploymentHelper.getDummyTestClasses())
                .addClass(TransactionManagerImplIntegrationTest.class);

        System.out.println("Deploying test archive: " + archive.toString(true));

        return archive;
    }

    @Before
    public void before() {
        Assert.assertNotNull("Transaction manager was not injected", transactionManager);
        Assert.assertNotNull("BA controller was not injected", baController);
        Assert.assertFalse("Transaction should not be running before the test", baController.isBARunning());

        DummyConfirmationAction.INVOCATIONS_COUNTER = 0;
        DummyCompensationAction.INVOCATIONS_COUNTER = 0;
    }

    @After
    public void after() {
        if (baController.isBARunning()) {
            try {
                baController.cancelBusinessActivity();
            } catch (Exception e) {
                LOGGER.warn(e);
            }
            Assert.fail("Transaction should not be running after the test");
        }
    }

    @Test
    public void shouldBeginAndCloseTheTransaction() throws WrongStateException, SystemException {
        LOGGER.info(getClass().getSimpleName() + "shouldBeginAndCloseTheTransaction starting");

        executeTest(true);
    }

    @Test
    public void shouldBeginAndCancelTheTransaction() throws WrongStateException, SystemException {
        LOGGER.info(getClass().getSimpleName() + "shouldBeginAndCancelTheTransaction starting");

        executeTest(false);
    }

    private void executeTest(final boolean success) throws WrongStateException, SystemException {
        final DummyConfirmationAction confirmationAction = new DummyConfirmationAction(new DummyState("initial"));
        final DummyCompensationAction compensationAction = new DummyCompensationAction(new DummyState("initial"));

        transactionManager.begin();
        transactionManager.register(confirmationAction, compensationAction);

        if (success) {
            transactionManager.close();
        } else {
            transactionManager.cancel();
        }

        Assert.assertEquals(success ? 1 : 0, DummyConfirmationAction.INVOCATIONS_COUNTER);
        Assert.assertEquals(success ? 0 : 1, DummyCompensationAction.INVOCATIONS_COUNTER);
        Assert.assertEquals(success ? "confirmed" : "initial", confirmationAction.getState().getValue());
        Assert.assertEquals(success ? "initial" : "compensated", compensationAction.getState().getValue());
    }

}
