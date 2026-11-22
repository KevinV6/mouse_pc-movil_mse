using System.IO;
using System.Text;

namespace receptor_pc.Services;

public sealed class FileLogger
{
    private readonly string _logDir;
    private readonly object _sync = new();

    public FileLogger(string? logDirectory = null)
    {
        _logDir = logDirectory
            ?? Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                "MseReceptor",
                "logs");
        Directory.CreateDirectory(_logDir);
    }

    public void Info(string message) => Write("INFO", message);
    public void Warn(string message) => Write("WARN", message);
    public void Error(string message, Exception? ex = null)
    {
        string full = ex is null ? message : $"{message} | {ex.GetType().Name}: {ex.Message}";
        Write("ERROR", full);
    }

    private void Write(string level, string message)
    {
        string line = $"{DateTimeOffset.Now:yyyy-MM-dd HH:mm:ss.fff} [{level}] {message}{Environment.NewLine}";
        string filePath = Path.Combine(_logDir, $"mse-{DateTime.Now:yyyyMMdd}.log");

        lock (_sync)
        {
            try
            {
                File.AppendAllText(filePath, line, Encoding.UTF8);
            }
            catch
            {
                // Never crash on log failure
            }
        }
    }
}
