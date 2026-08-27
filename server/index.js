const express=require('express');
const cors=require('cors');
const path=require('path');
require('dotenv').config();

const app=express();
app.use(cors());
app.use(express.json({limit:'12mb'}));
app.use(express.static(path.join(__dirname,'../client')));

app.post('/api/identify', async (req,res)=>{
  if(!process.env.AZURE_OPENAI_ENDPOINT || !process.env.AZURE_OPENAI_KEY || !process.env.AZURE_DEPLOYMENT_NAME){
    return res.status(503).json({error:'Azure OpenAI is not configured yet.'});
  }
  // Credentials are intentionally read only from environment variables.
  // Add the Azure vision request here when the deployment details are supplied.
  return res.status(501).json({error:'Azure coin-image identification is ready for configuration.'});
});

app.post('/api/ebay/draft', async (req,res)=>{
  if(!process.env.EBAY_CLIENT_ID || !process.env.EBAY_CLIENT_SECRET){
    return res.status(503).json({error:'eBay developer credentials are not configured yet.'});
  }
  return res.status(501).json({message:'eBay credentials detected; listing creation can now be wired to the chosen eBay marketplace/account.'});
});

app.get('*',(req,res)=>res.sendFile(path.join(__dirname,'../client/index.html')));
app.listen(process.env.PORT||3000,()=>console.log('Coin AI server listening on '+(process.env.PORT||3000)));
