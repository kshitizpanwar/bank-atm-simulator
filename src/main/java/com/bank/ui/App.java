package com.bank.ui;

import com.bank.dao.AccountDaoJdbc;
import com.bank.dao.AdminDaoJdbc;
import com.bank.dao.TransactionDaoJdbc;
import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.security.PasswordHasher;
import com.bank.service.AccountService;
import com.bank.service.AuthService;
import com.bank.ui.presenter.BalancePresenter;
import com.bank.ui.presenter.ChangePinPresenter;
import com.bank.ui.presenter.DepositPresenter;
import com.bank.ui.presenter.LoginPresenter;
import com.bank.ui.presenter.MenuPresenter;
import com.bank.ui.presenter.MiniStatementPresenter;
import com.bank.ui.presenter.OpenAccountPresenter;
import com.bank.ui.presenter.TransferPresenter;
import com.bank.ui.presenter.WithdrawPresenter;
import com.bank.ui.view.BalanceViewFx;
import com.bank.ui.view.ChangePinViewFx;
import com.bank.ui.view.DepositViewFx;
import com.bank.ui.view.LoginViewFx;
import com.bank.ui.view.MenuViewFx;
import com.bank.ui.view.MiniStatementViewFx;
import com.bank.ui.view.OpenAccountViewFx;
import com.bank.ui.view.TransferViewFx;
import com.bank.ui.view.WithdrawViewFx;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application implements Navigator {

    private Stage stage;
    private final Session session = new Session();

    private AuthService authService;
    private AccountService accountService;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;

        Database db = Database.defaultInstance();
        SchemaInitializer.initialize(db);
        UnitOfWork uow = new UnitOfWork(db);
        PasswordHasher hasher = new PasswordHasher();
        this.authService = new AuthService(uow, new AccountDaoJdbc(), new AdminDaoJdbc(), hasher);
        this.accountService = new AccountService(uow, new AccountDaoJdbc(), new TransactionDaoJdbc(), hasher);

        primaryStage.setTitle("ATM");
        showLogin();
        primaryStage.show();
    }

    private void setRoot(Parent root) {
        if (stage.getScene() == null) {
            stage.setScene(new Scene(root, 360, 480));
        } else {
            stage.getScene().setRoot(root);
        }
    }

    @Override
    public void showLogin() {
        LoginViewFx view = new LoginViewFx();
        new LoginPresenter(view, authService, this, session);
        setRoot(view.getRoot());
    }

    @Override
    public void showOpenAccount() {
        OpenAccountViewFx view = new OpenAccountViewFx();
        new OpenAccountPresenter(view, accountService, this);
        setRoot(view.getRoot());
    }

    @Override
    public void showMenu() {
        MenuViewFx view = new MenuViewFx();
        new MenuPresenter(view, this, session);
        setRoot(view.getRoot());
    }

    @Override
    public void showBalance() {
        BalanceViewFx view = new BalanceViewFx();
        BalancePresenter presenter = new BalancePresenter(view, accountService, session, this);
        presenter.load();
        setRoot(view.getRoot());
    }

    @Override
    public void showDeposit() {
        DepositViewFx view = new DepositViewFx();
        new DepositPresenter(view, accountService, session, this);
        setRoot(view.getRoot());
    }

    @Override
    public void showWithdraw() {
        WithdrawViewFx view = new WithdrawViewFx();
        new WithdrawPresenter(view, accountService, session, this);
        setRoot(view.getRoot());
    }

    @Override
    public void showTransfer() {
        TransferViewFx view = new TransferViewFx();
        new TransferPresenter(view, accountService, session, this);
        setRoot(view.getRoot());
    }

    @Override
    public void showMiniStatement() {
        MiniStatementViewFx view = new MiniStatementViewFx();
        MiniStatementPresenter presenter = new MiniStatementPresenter(view, accountService, session, this);
        presenter.load();
        setRoot(view.getRoot());
    }

    @Override
    public void showChangePin() {
        ChangePinViewFx view = new ChangePinViewFx();
        new ChangePinPresenter(view, accountService, session, this);
        setRoot(view.getRoot());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
