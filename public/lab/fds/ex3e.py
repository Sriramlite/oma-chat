import pandas as pd
population_dict={'california':38332521,'texes':26448193,'New york':19651127,'florida':19552860,'illinois':12882135}
population=pd.Series(population_dict)
area_dict={'california':423967,'texes':695662,'New york':141297,'florida':170312,'illinois':149995}
area=pd.Series(area_dict)
states=pd.DataFrame({'population':population,'area':area})
print(states['area'])
