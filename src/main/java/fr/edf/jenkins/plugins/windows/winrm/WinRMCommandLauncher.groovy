package fr.edf.jenkins.plugins.windows.winrm

import java.util.logging.Level
import java.util.logging.Logger

import org.antlr.v4.runtime.misc.NotNull
import org.kohsuke.accmod.Restricted
import org.kohsuke.accmod.restrictions.NoExternalUse

import fr.edf.jenkins.plugins.windows.winrm.client.WinRMException
import fr.edf.jenkins.plugins.windows.winrm.client.WinRMTool
import fr.edf.jenkins.plugins.windows.winrm.client.output.CommandOutput
import fr.edf.jenkins.plugins.windows.winrm.connection.WinRMConnectionConfiguration
import fr.edf.jenkins.plugins.windows.winrm.connection.WinRMConnectionException
import fr.edf.jenkins.plugins.windows.winrm.connection.WinRMConnectionFactory

/**
 * Contains method needed to launch commands
 * @author CHRIS BAHONDA
 * @author Mathieu Delrocq
 *
 */
class WinRMCommandLauncher {

    private static final Logger LOGGER = Logger.getLogger(WinRMCommandLauncher.class.name)
    private static final String LOG_SEPARATOR = "####################  "

    WinRMTool winrmTool
    String commandId
    String shellId

    /**
     * Construct WinRMCommandLauncher with its own WinRmClient
     * @param connectionConfiguration
     */
    protected WinRMCommandLauncher(@NotNull WinRMConnectionConfiguration connectionConfiguration) throws WinRMConnectionException {
        this.winrmTool = WinRMConnectionFactory.getWinRMConnection(connectionConfiguration)
    }

    /**
     * Open a shell on the remote machine
     * @return shellId
     * @throws WinRMCommandException
     */
    protected String openShell() throws WinRMCommandException {
        LOGGER.log(Level.FINEST, LOG_SEPARATOR + "OPEN SHELL")
        try {
            return winrmTool.openShell()
        }catch(WinRMException winrme) {
            throw new WinRMCommandException("Unable to open a shell", winrme)
        }
    }

    /**
     * Stop a command still running
     * @param commandId
     * @throws WinRMCommandException
     */
    protected void cleanupCommand(String commandId) throws WinRMCommandException {
        commandId = commandId ?: this.commandId
        LOGGER.log(Level.FINEST, LOG_SEPARATOR + "CLEANUP COMMAND WITH ID " + commandId)
        try {
            winrmTool.cleanupCommand(shellId, commandId)
        }catch(WinRMException winrme) {
            throw new WinRMCommandException("Unable to cleanup the command with id " + commandId, winrme)
        }
    }

    /**
     * Close the shell
     * @throws WinRMException
     */
    protected void closeShell() throws WinRMException {
        LOGGER.log(Level.FINEST, LOG_SEPARATOR + "CLOSE SHELL WITH ID " + shellId)
        try {
            winrmTool.deleteShellRequest(shellId)
            this.shellId = null
        }catch(WinRMException winrme) {
            throw new WinRMCommandException("Unable to close the shell with id " + shellId, winrme)
        }
    }

    /**
     * Execute the command using the given connection configuration
     * @param command : the powershell command to launch
     * @param ignoreError : do not throw exception if status code != 0
     * @param keepAlive : if true, keep the shell open. To close it, you must call the method closeShell manually or call an other command wich doesn't keepAlive
     * @return command result
     * @throws Exception
     */
    @Restricted(NoExternalUse)
    protected String executeCommand(@NotNull String command, @NotNull boolean ignoreError, @NotNull boolean keepAlive) throws WinRMCommandException {
        CommandOutput output = null
        String commandId = null
        try {
            shellId = shellId ?: openShell()
            LOGGER.log(Level.FINEST, LOG_SEPARATOR + "EXECUTE COMMAND " + command)
            commandId = winrmTool.executePSCommand(command)
            this.commandId = commandId ?: this.commandId
            LOGGER.log(Level.FINEST, LOG_SEPARATOR + "GET COMMAND OUTPUT WITH ID " + commandId)
            output = winrmTool.getCommandOutput(shellId, commandId)

            if(!ignoreError && output.exitStatus!=0) {
                closeShell()
                throw new Exception("OUTPUT : " + output.output + " ;ERROR : " + output.errorOutput)
            }
            if(!keepAlive) {
                closeShell()
            }
            return output.output
        } catch(WinRMException we) {
            if(shellId != null) {
                closeShell()
            }
            throw new WinRMCommandException("Unable to execute the command " + command, we)
        }
    }
}
