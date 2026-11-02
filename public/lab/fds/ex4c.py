import requests
from bs4 import BeautifulSoup
url='https://google.com'
resp=requests.get(url)
if(resp.status_code)==200:
    print("Successfully opened the webpage")
    soup=BeautifulSoup(resp.content,'html.parser')
    t=soup.title
    print(t.get_text())
else:
    print("Error")
