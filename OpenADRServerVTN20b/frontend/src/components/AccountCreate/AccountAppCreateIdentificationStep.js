
import React from 'react';

import Typography from '@material-ui/core/Typography';

import FormControl from '@material-ui/core/FormControl';

import TextField from '@material-ui/core/TextField';



import Grid from '@material-ui/core/Grid';




import Divider from '@material-ui/core/Divider';


import HelpIcon from '@material-ui/icons/Help';

import UserCreateIdentificationSSLCertificatePanel from '../common/UserCreateIdentificationSSLCertificatePanel'

export class AccountAppCreateIdentificationStep extends React.Component {


  handleCommonNameChange = (e) => {
    var identification = this.props.identification;
    identification.commonName = e.target.value;
    this.props.onChange(identification);
  }


  render() {
    const {classes, identification, vtnConfiguration} = this.props;
   

    return (

    <Grid container
          spacing={ 8 }
          justify="center">
      <Grid container spacing={ 24 }>
        <Grid item xs={ 2 } />
        <Grid item xs={ 4 }>
           <FormControl className={ classes.formControl }>
            <TextField required
                       id="oadr_cn_textfield"
                       fullWidth={ true }
                       label="App Common Name"
                       placeholder="myven.oadr.com"
                       value={ identification.commonName }
                       className={ classes.textField }
                       error={ this.props.hasError }
                       onChange={ this.handleCommonNameChange }
                       InputLabelProps={ { shrink: true, } } />
          </FormControl>
        </Grid>
        <Grid item xs={ 4 }>
         
        </Grid>
        <Grid item xs={ 2 } />
      </Grid>
      <Grid container
            style={ { marginTop: 20 } }
            spacing={ 24 }>
        <Grid item xs={ 2 } />
        <Grid item xs={ 8 }>
          <Divider />
        </Grid>
        <Grid item xs={ 2 } />
      </Grid>
      <Grid container
            style={ { marginTop: 20 } }
            spacing={ 24 }>
        <Grid item xs={ 2 } />
        <Grid item xs={ 8 }>
          <UserCreateIdentificationSSLCertificatePanel classes={classes}
            identification={identification}
            vtnConfiguration={vtnConfiguration}
            onChange={this.props.onChange} />
        </Grid>
        <Grid item xs={ 2 } />
      </Grid>
      <Grid container
            style={ { marginTop: 20 } }
            spacing={ 24 }>
        <Grid item xs={ 2 } />
        <Grid item xs={ 1 }>
          <HelpIcon color="disabled" style={ { width: 40, height: 40 } } />
        </Grid>
        <Grid item xs={ 6 }>
          <Typography variant="caption" gutterBottom>
            VEN MUST have a valid SSL certificate to securely communicate with VTN. VEN identifier (VenID) is a fingerprint of VEN certificate.
          </Typography>
          <Typography variant="caption" gutterBottom>
            In case VEN certificate is not generated by VTN, user have to provide VenID corresponding to it's certificate fingerprint
          </Typography>
          <Typography variant="caption" gutterBottom>
            In case VEN certificate is generated by VTN, VenID will be computed after certificate generation.
            Ven Common Name is used as certificate CN subject entry
            Certificate will available for download at the confirmation of this form and only at that time.
            VTN can't re-generate certificate without re-creating a VEN.
          </Typography>
        </Grid>
        <Grid item xs={ 2 } />
      </Grid>
    </Grid>
    );
  }
}

export default AccountAppCreateIdentificationStep;