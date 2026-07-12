package com.bank.ui;

import com.bank.dao.AccountDaoJdbc;
import com.bank.dao.AdminDaoJdbc;
import com.bank.dao.TransactionDaoJdbc;
import com.bank.db.Database;
import com.bank.db.SchemaInitializer;
import com.bank.db.UnitOfWork;
import com.bank.security.PasswordHasher;
import com.bank.service.AccountService;
import com.bank.service.AdminService;
import com.bank.service.AuthService;
import com.bank.ui.presenter.AdminHomePresenter;
import com.bank.ui.presenter.AdminLoginPresenter;
import com.bank.ui.presenter.AdminOpenAccountPresenter;
import com.bank.ui.presenter.AllAccountsPresenter;
import com.bank.ui.presenter.BalancePresenter;
import com.bank.ui.presenter.ChangePinPresenter;
import com.bank.ui.presenter.DepositPresenter;
import com.bank.ui.presenter.LoginPresenter;
import com.bank.ui.presenter.ManageAccountPresenter;
import com.bank.ui.presenter.MenuPresenter;
import com.bank.ui.presenter.MiniStatementPresenter;
import com.bank.ui.presenter.OpenAccountPresenter;
import com.bank.ui.presenter.TransferPresenter;
import com.bank.ui.presenter.WithdrawPresenter;
import com.bank.ui.view.AdminHomeViewFx;
import com.bank.ui.view.AdminLoginViewFx;
import com.bank.ui.view.AdminOpenAccountViewFx;
import com.bank.ui.view.AllAccountsViewFx;
import com.bank.ui.view.BalanceViewFx;
import com.bank.ui.view.ChangePinViewFx;
import com.bank.ui.view.DepositViewFx;
import com.bank.ui.view.LoginViewFx;
import com.bank.ui.view.ManageAccountViewFx;
import com.bank.ui.view.MenuViewFx;
import com.bank.ui.view.MiniStatementViewFx;
import com.bank.ui.view.OpenAccountViewFx;
import com.bank.ui.view.RoleSelectViewFx;
import com.bank.ui.view.SidebarFx;
import com.bank.ui.view.TransferViewFx;
import com.bank.ui.view.WithdrawViewFx;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class App extends Application implements Navigator, AdminNavigator {

    private Stage stage;
    private final Session session = new Session();
    private final AdminSession adminSession = new AdminSession();

    private AuthService authService;
    private AccountService accountService;
    private AdminService adminService;

    private SidebarFx sidebar;
    private StackPane contentPane;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;

        Database db = Database.defaultInstance();
        SchemaInitializer.initialize(db);
        UnitOfWork uow = new UnitOfWork(db);
        PasswordHasher hasher = new PasswordHasher();
        this.authService = new AuthService(uow, new AccountDaoJdbc(), new AdminDaoJdbc(), hasher);
        this.accountService = new AccountService(uow, new AccountDaoJdbc(), new TransactionDaoJdbc(), hasher);
        this.adminService = new AdminService(uow, new AdminDaoJdbc(), hasher);
        this.adminService.ensureDefaultAdmin();

        primaryStage.setTitle("Bank");
        showRoleSelect();
        primaryStage.show();
    }

    // ---- root/content placement ----

    private void applyRoot(Parent root) {
        if (stage.getScene() == null) {
            Scene scene = new Scene(root, 940, 620);
            scene.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
            stage.setScene(scene);
        } else {
            stage.getScene().setRoot(root);
        }
    }

    /** Full-screen card-wrapped screens (customer flow, role select, admin login). */
    private void setRoot(Parent root) {
        root.getStyleClass().add("card");
        StackPane background = new StackPane(root);
        background.getStyleClass().add("app-background");
        applyRoot(background);
    }

    /** Dashboard shell — fills the window, no card wrapper. */
    private void setRootRaw(Parent root) {
        applyRoot(root);
    }

    /** Render a section into the dashboard content region (sidebar stays). */
    private void setContent(Parent node) {
        contentPane.getChildren().setAll(node);
    }

    // ---- customer flow (unchanged) ----

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

    // ---- role select + admin login (full screen) ----

    @Override
    public void showRoleSelect() {
        RoleSelectViewFx view = new RoleSelectViewFx();
        view.setOnCustomer(this::showLogin);
        view.setOnAdmin(this::showAdminLogin);
        setRoot(view.getRoot());
    }

    @Override
    public void showAdminLogin() {
        AdminLoginViewFx view = new AdminLoginViewFx();
        new AdminLoginPresenter(view, authService, this, adminSession);
        setRoot(view.getRoot());
    }

    // ---- admin dashboard ----

    @Override
    public void showAdminMenu() {
        sidebar = new SidebarFx();
        sidebar.setOnHome(this::showAdminHome);
        sidebar.setOnAllAccounts(this::showAllAccounts);
        sidebar.setOnOpenAccount(this::showAdminOpenAccount);
        sidebar.setOnLogout(() -> {
            adminSession.clear();
            showRoleSelect();
        });
        contentPane = new StackPane();
        contentPane.getStyleClass().add("content-pane");
        BorderPane dashboard = new BorderPane();
        dashboard.setLeft(sidebar.getRoot());
        dashboard.setCenter(contentPane);
        setRootRaw(dashboard);
        showAdminHome();
    }

    @Override
    public void showAdminHome() {
        AdminHomeViewFx view = new AdminHomeViewFx();
        new AdminHomePresenter(view, accountService).load();
        sidebar.setActive("home");
        setContent(view.getRoot());
    }

    @Override
    public void showAllAccounts() {
        AllAccountsViewFx view = new AllAccountsViewFx();
        AllAccountsPresenter presenter = new AllAccountsPresenter(view, accountService, adminSession, this);
        presenter.load();
        sidebar.setActive("accounts");
        setContent(view.getRoot());
    }

    @Override
    public void showAdminOpenAccount() {
        AdminOpenAccountViewFx view = new AdminOpenAccountViewFx();
        new AdminOpenAccountPresenter(view, accountService, this);
        sidebar.setActive("open");
        setContent(view.getRoot());
    }

    @Override
    public void showManageAccount(long accountNumber) {
        ManageAccountViewFx view = new ManageAccountViewFx();
        ManageAccountPresenter presenter = new ManageAccountPresenter(view, accountService, adminSession, this);
        presenter.load();
        sidebar.setActive("accounts");
        setContent(view.getRoot());
    }

    public static void main(String[] args) {
        launch(args);
    }
}
