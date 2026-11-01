import pandas as pd
area=pd.Series({'California':423967,'Texas':695665,'New York':141297,'Florida':170321,'illinois':12882135})
pop=pd.Series({'California':38332521,'Texas':26448193,'New York':19651127,'Florida':19552860,'illinois':12882135})
density=pd.Series({'California':90.413926,'Texas':38.018740,'New York':139.076746,'Florida':114.806121,'illinois':85.883763})
data=pd.DataFrame({'area':area,'pop':pop,'density':density})
print(data.values)
print(data.T)
print(data.iloc[:3,:2])
print(data.loc[:'illinois','pop'])
print(data.loc[data.density>100,['pop','density']])

               
