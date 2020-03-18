private static string OpenLicense(string path)
 {
        CxLogger.LogError("path = " + path);
        if (!File.Exists(path))
        {
            Checkmarx.Licensing.Log.Write("license cannot be found at " + path);
            throw new Exception("No License");
        }
        else
        {  
            try
            {
                using (StreamReader sr = new StreamReader(path))
                {
                    int i = 78;
                    //i--;
                    // another comment
                    return sr.ReadToEnd();
                }
            }
            catch
            {
                Checkmarx.Licensing.Log.Write("Code -960: check open l");
                throw;
            }
        }
     
        cout<<"hello world"<<endl;
}